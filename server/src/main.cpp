#include <pistache/endpoint.h>
#include <pistache/http.h>
#include <pistache/router.h>
#include <iostream>
#include <memory>
#include <signal.h>

#include "auth/jwt_manager.h"
#include "auth/device_registry.h"

using namespace Pistache;

class ARCSServer {
public:
    ARCSServer(Address addr)
        : httpEndpoint_(std::make_shared<Http::Endpoint>(addr)),
          jwt_manager_("your-secret-key-change-me", 24),
          device_registry_()
    {
        auto opts = Http::Endpoint::options()
            .threads(std::thread::hardware_concurrency())
            .flags(Tcp::Options::ReuseAddr);
        httpEndpoint_->init(opts);
        setupRoutes();
    }
    
    void start() {
        std::cout << "ARCS Server starting..." << std::endl;
        httpEndpoint_->setHandler(router_.handler());
        httpEndpoint_->serve();
    }
    
    void stop() {
        std::cout << "ARCS Server stopping..." << std::endl;
        httpEndpoint_->shutdown();
    }

private:
    void setupRoutes() {
        using namespace Rest;
        
        Routes::Get(router_, "/health", 
            Routes::bind(&ARCSServer::handleHealth, this));
        Routes::Post(router_, "/api/devices/register",
            Routes::bind(&ARCSServer::handleRegister, this));
    }
    
    void handleHealth(const Rest::Request& /*request*/, 
                     Http::ResponseWriter response) {
        response.send(Http::Code::Ok, "{\"status\":\"ok\"}");
    }
    
    void handleRegister(const Rest::Request& request,
                       Http::ResponseWriter response) {
        // TODO: Implement device registration
        response.send(Http::Code::Ok, "{\"success\":true}");
    }
    
    std::shared_ptr<Http::Endpoint> httpEndpoint_;
    Rest::Router router_;
    arcs::auth::JWTManager jwt_manager_;
    arcs::auth::DeviceRegistry device_registry_;
};

int main(int argc, char* argv[]) {
    int port = 9080;
    if (argc > 1) {
        port = std::atoi(argv[1]);
    }
    
    Address addr(Ipv4::any(), Port(port));
    ARCSServer server(addr);
    
    // Signal handling
    signal(SIGINT, [](int) {
        std::cout << "\nShutting down..." << std::endl;
        exit(0);
    });
    
    server.start();
    
    return 0;
}
