#include <QApplication>
#include "ui/main_window.h"

int main(int argc, char *argv[]) {
    QApplication app(argc, argv);
    
    app.setApplicationName("ARCS PC Controller");
    app.setApplicationVersion("1.0.0");
    app.setOrganizationName("ARCS");
    
    MainWindow window;
    window.show();
    
    return app.exec();
}
