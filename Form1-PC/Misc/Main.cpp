#include "Config/AppConfig.h"
#include "Config/AppState.h"
#include "GUI/Form1.h"
#include <QApplication>
#include <QDir>
#include <QLocalServer>
#include <QLocalSocket>
#include <QStandardPaths>
#include <QStyleFactory>
#include "SoundDevices/SoundDeviceBass.h"

static bool IsInstanceAlreadyRunning(QString appName)
{
  QLocalSocket socket;
  socket.connectToServer(appName);
  const bool isOpen = socket.isOpen();
  socket.close();
  return isOpen;
}

static void SetStyleSheet()
{
  QApplication::setStyle(QStyleFactory::create("fusion"));
  QPalette palette;

  palette.setColor(QPalette::WindowText, QColor(200, 200, 200, 255));
  palette.setColor(QPalette::Button, QColor(100, 100, 100, 255));
  palette.setColor(QPalette::Light, QColor(97, 97, 97, 255));
  palette.setColor(QPalette::Midlight, QColor(59, 59, 59, 255));
  palette.setColor(QPalette::Dark, QColor(37, 37, 37, 255));
  palette.setColor(QPalette::Mid, QColor(45, 45, 45, 255));
  palette.setColor(QPalette::Text, QColor(200, 200, 200, 255));
  palette.setColor(QPalette::BrightText, QColor(37, 37, 37, 255));
  palette.setColor(QPalette::ButtonText, QColor(200, 200, 200, 255));
  palette.setColor(QPalette::Base, QColor(42, 42, 42, 255));
  palette.setColor(QPalette::Window, QColor(68, 68, 68, 255));
  palette.setColor(QPalette::Shadow, QColor(0, 0, 0, 255));
  palette.setColor(QPalette::Highlight, QColor(143, 58, 255, 255));
  palette.setColor(QPalette::HighlightedText, QColor(255, 255, 255, 255));
  palette.setColor(QPalette::Link, QColor(0, 148, 255, 255));
  palette.setColor(QPalette::LinkVisited, QColor(255, 0, 220, 255));
  palette.setColor(QPalette::AlternateBase, QColor(46, 46, 46, 255));
  QBrush NoRoleBrush(QColor(0, 0, 0, 255), Qt::NoBrush);
  palette.setBrush(QPalette::NoRole, NoRoleBrush);
  palette.setColor(QPalette::ToolTipBase, QColor(255, 255, 220, 255));
  palette.setColor(QPalette::ToolTipText, QColor(0, 0, 0, 255));

  palette.setColor(QPalette::Disabled, QPalette::WindowText, QColor(128, 128, 128, 255));
  palette.setColor(QPalette::Disabled, QPalette::Button, QColor(80, 80, 80, 255));
  palette.setColor(QPalette::Disabled, QPalette::Text, QColor(105, 105, 105, 255));
  palette.setColor(QPalette::Disabled, QPalette::BrightText, QColor(255, 255, 255, 255));
  palette.setColor(QPalette::Disabled, QPalette::ButtonText, QColor(128, 128, 128, 255));
  palette.setColor(QPalette::Disabled, QPalette::Highlight, QColor(86, 117, 148, 255));

  QApplication::setPalette(palette);
}

int main(int argc, char** argv)
{
  QApplication app(argc, argv);

  if (IsInstanceAlreadyRunning("Form1"))
    return 1;

  QCoreApplication::setOrganizationDomain("www.ArtifactGames.de");
  QCoreApplication::setOrganizationName("ArtifactGames");
  QCoreApplication::setApplicationName("Form1");
  SetStyleSheet();

  const QString sAppDir = QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation);

  // make sure the app dir exists
  {
    QDir d;
    d.mkpath(sAppDir);
  }

  int result = 0;
  {
    SoundDevice::s_pSingleton = new SoundDeviceBass();
    SoundDevice::s_pSingleton->Startup();

    AppConfig config;
    MusicLibrary library;
    AppState state;

    config.Load(sAppDir);

    library.Startup(sAppDir);
    state.Startup();

    Form1* mainWnd = new Form1();
    mainWnd->show();

    result = app.exec();
    delete mainWnd;

    config.Save(sAppDir);

    SoundDevice::s_pSingleton->Shutdown();
    delete SoundDevice::s_pSingleton;
  }

  return result;
}
