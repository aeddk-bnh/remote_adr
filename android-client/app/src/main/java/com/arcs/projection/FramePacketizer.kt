package com.arcs.client.projection

import android.media.MediaCodec
import timber.log.Timber
import java.nio.ByteBuffer

/**
 * Packetizes encoded video frames for network transmission
 * Adds frame metadata and fragmentation for large frames
 */
class FramePacketizer(
    private val maxPacketSize: Int = 65536  // 64 KB max packet size
) {
    
    companion object {
        private val MAGIC = byteArrayOf('A'.code.toByte(), 'R'.code.toByte(), 
                                        'C'.code.toByte(), 'S'.code.toByte())
        private const val VERSION: Byte = 0x01
        private const val TYPE_VIDEO_FRAME: Byte = 0x02
        
        // Frame flags
        const val FLAG_KEYFRAME: Byte = 0x01
        const val FLAG_ENCRYPTED: Byte = 0x02
        const val FLAG_FRAGMENT: Byte = 0x04
    }
    
    private var frameNumber: UInt = 0u
    
    /**
     * Packetize encoded frame data
     * Returns list of packets (may be fragmented if frame > maxPacketSize)
     */
    fun packetize(
        data: ByteBuffer,
        info: MediaCodec.BufferInfo,
        isKeyFrame: Boolean = false,
        isEncrypted: Boolean = false
    ): List<ByteArray> {
        // Defensive checks
        if (info.size <= 0) {
            Timber.w("[FramePacketizer] Invalid frame size: %d", info.size)
            return emptyList()
        }
        
        if (info.offset < 0 || info.offset + info.size > data.capacity()) {
            Timber.e("[FramePacketizer] Invalid buffer bounds: offset=%d, size=%d, capacity=%d",
                info.offset, info.size, data.capacity())
            return emptyList()
        }
        
        // Ensure ByteBuffer is positioned correctly
        data.position(info.offset)
        data.limit(info.offset + info.size)
        
        val frameData = ByteArray(info.size)
        try {
            data.get(frameData, 0, info.size)
        } catch (e: Exception) {
            Timber.e(e, "[FramePacketizer] Error reading from ByteBuffer")
            return emptyList()
        }
        
        val packets = mutableListOf<ByteArray>()
        val timestamp = info.presentationTimeUs
        
        // Check if fragmentation needed
        val needsFragmentation = frameData.size > (maxPacketSize - 256)
        
        if (!needsFragmentation) {
            // Single packet
            val packet = createPacket(
                frameNumber,
                timestamp,
                frameData,
                isKeyFrame,
                isEncrypted,
                false
            )
            packets.add(packet)
            Timber.d("[FramePacketizer] Frame #%d: single packet, size=%d bytes, isKey=%s, ts=%d us",
                frameNumber.toInt(), packet.size, isKeyFrame, timestamp)
        } else {
            // Fragment into multiple packets
            var offset = 0
            var fragmentIndex = 0
            
            while (offset < frameData.size) {
                val chunkSize = minOf(maxPacketSize - 256, frameData.size - offset)
                val chunk = frameData.copyOfRange(offset, offset + chunkSize)
                
                val packet = createPacket(
                    frameNumber,
                    timestamp,
                    chunk,
                    isKeyFrame && fragmentIndex == 0,
                    isEncrypted,
                    true,
                    fragmentIndex,
                    (frameData.size + maxPacketSize - 257) / (maxPacketSize - 256)
                )
                
                packets.add(packet)
                offset += chunkSize
                fragmentIndex++
            }
            
            Timber.d("Frame $frameNumber fragmented into ${packets.size} packets")
        }
        
        frameNumber++
        return packets
    }
    
    /**
     * Create packet with ARCS protocol header
     * Format: [Magic:4][Version:1][Type:1][FrameNum:4][Timestamp:8][Flags:1]
     *         [PayloadLen:4][FragmentInfo:4?][Payload:N][CRC32:4]
     */
    private fun createPacket(
        frameNum: UInt,
        timestamp: Long,
        payload: ByteArray,
        isKeyFrame: Boolean,
        isEncrypted: Boolean,
        isFragment: Boolean,
        fragmentIndex: Int = 0,
        totalFragments: Int = 1
    ): ByteArray {
        val hasFragmentInfo = isFragment
        val headerSize = 22 + (if (hasFragmentInfo) 4 else 0)
        val totalSize = headerSize + payload.size + 4  // +4 for CRC
        
        val packet = ByteArray(totalSize)
        var offset = 0
        // Magic (4 bytes)
        System.arraycopy(MAGIC, 0, packet, offset, 4)
        offset += 4
        
        // Version (1 byte)
        packet[offset++] = VERSION
        
        // Type (1 byte)
        packet[offset++] = TYPE_VIDEO_FRAME
        
        // Frame number (4 bytes, big-endian)
        packet[offset++] = (frameNum shr 24).toByte()
        packet[offset++] = (frameNum shr 16).toByte()
        packet[offset++] = (frameNum shr 8).toByte()
        packet[offset++] = frameNum.toByte()
        
        // Timestamp (8 bytes, big-endian)
        packet[offset++] = (timestamp shr 56).toByte()
        packet[offset++] = (timestamp shr 48).toByte()
        packet[offset++] = (timestamp shr 40).toByte()
        packet[offset++] = (timestamp shr 32).toByte()
        packet[offset++] = (timestamp shr 24).toByte()
        packet[offset++] = (timestamp shr 16).toByte()
        packet[offset++] = (timestamp shr 8).toByte()
        packet[offset++] = timestamp.toByte()
        
        // Flags (1 byte)
        var flags: Byte = 0
        if (isKeyFrame) flags = (flags.toInt() or FLAG_KEYFRAME.toInt()).toByte()
        if (isEncrypted) flags = (flags.toInt() or FLAG_ENCRYPTED.toInt()).toByte()
        if (isFragment) flags = (flags.toInt() or FLAG_FRAGMENT.toInt()).toByte()
        packet[offset++] = flags
        
        // Payload length (4 bytes, big-endian)
        val payloadLen = payload.size
        packet[offset++] = (payloadLen shr 24).toByte()
        packet[offset++] = (payloadLen shr 16).toByte()
        packet[offset++] = (payloadLen shr 8).toByte()
        packet[offset++] = payloadLen.toByte()
        
        // Fragment info (4 bytes if fragmented)
        if (hasFragmentInfo) {
            packet[offset++] = (fragmentIndex shr 8).toByte()
            packet[offset++] = fragmentIndex.toByte()
            packet[offset++] = (totalFragments shr 8).toByte()
            packet[offset++] = totalFragments.toByte()
        }
        
        // Payload
        System.arraycopy(payload, 0, packet, offset, payload.size)
        offset += payload.size
        
        // CRC32 checksum (4 bytes)
        val crc = calculateCRC32(packet, 0, offset)
        packet[offset++] = (crc shr 24).toByte()
        packet[offset++] = (crc shr 16).toByte()
        packet[offset++] = (crc shr 8).toByte()
        packet[offset] = crc.toByte()  // Last byte - no increment
        
        return packet
    }
    
    /**
     * Calculate CRC32 checksum
     */
    private fun calculateCRC32(data: ByteArray, offset: Int, length: Int): Int {
        val crc32 = java.util.zip.CRC32()
        crc32.update(data, offset, length)
        return crc32.value.toInt()
    }
    
    /**
     * Reset frame counter
     */
    fun reset() {
        frameNumber = 0u
        Timber.d("Frame packetizer reset")
    }
}
