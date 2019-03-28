cmake_minimum_required(VERSION 2.8.12)

project(Form1 LANGUAGES CXX)

find_package(Qt5 COMPONENTS Widgets Gui Core Multimedia WinExtras REQUIRED)

set (FILES_TO_UI
  "Form1.ui"
  "SettingsDlg.ui"
  "SongInfoDlg.ui"
  "SmartPlaylistDlg.ui"
)

set (FILES_TO_MOC
  "Form1.h"
  "Playlist.h"
  "Sidebar.h"
  "AppState.h"
  "AllSongsPlaylist.h"
  "RegularPlaylist.h"
  "SmartPlaylist.h"
  "MusicSource.h"
  "MusicSourceFolder.h"
  "MusicLibrary.h"
  "AppConfig.h"
  "SettingsDlg.h"
  "TracklistView.h"
  "SongInfoDlg.h"
  "SoundDevice.h"
  "SoundDeviceQt.h"
  "SoundDeviceBass.h"
  "SmartPlaylistDlg.h"
)

set (FILE_TO_QRC
  "Form1.qrc"
)

QT5_WRAP_UI(UI_HEADERS ${FILES_TO_UI})
QT5_WRAP_CPP(MOC_FILES ${FILES_TO_MOC})
QT5_ADD_RESOURCES(QRC_FILES ${FILE_TO_QRC})

include_directories (${CMAKE_BINARY_DIR})

find_path(TAGLIB_INCLUDE_DIR taglib/tag.h)
find_library(TAGLIB_LIBRARY tag)
include_directories(${TAGLIB_INCLUDE_DIR})
link_directories(${CMAKE_CURRENT_SOURCE_DIR})

find_package(sqlite3 REQUIRED)

source_group (QT\\MOC FILES ${MOC_FILES})
source_group (QT\\UI FILES ${UI_HEADERS})
source_group (QT FILES ${FILES_TO_UI})
source_group (QT\\QRC FILES ${FILE_TO_QRC} ${QRC_FILES})

add_executable(${PROJECT_NAME} WIN32
	"Main.cpp"
	"Form1.cpp"
	"MusicLibrary.cpp"
	"Playlist.cpp"
	"Sidebar.cpp"
	"AppState.cpp"
	"Utils.cpp"
  "Song.h"
  "AllSongsPlaylist.cpp"
  "RegularPlaylist.cpp"
  "SmartPlaylist.cpp"
  "MusicSource.cpp"
  "MusicSourceFolder.cpp"
  "AppConfig.cpp"
  "SettingsDlg.cpp"
  "TracklistView.cpp"
  "SongInfoDlg.cpp"
  "ModificationRecorder.h"
  "Common.h"
  "resource.h"
  "App.rc"
  "SoundDeviceQt.cpp"
  "SoundDeviceBass.cpp"
  "SmartPlaylistDlg.cpp"
  "SmartPlaylistQuery.h"
  "SmartPlaylistQuery.cpp"
  ${FILE_TO_QRC}
  ${FILES_TO_MOC}
	${FILES_TO_UI}
	${UI_HEADERS}
	${MOC_FILES}
  ${QRC_FILES}
)

target_link_libraries(${PROJECT_NAME} sqlite3 ${TAGLIB_LIBRARY})
target_link_libraries(${PROJECT_NAME} Qt5::Widgets Qt5::Core Qt5::Multimedia Qt5::Gui Qt5::WinExtras Qt5::WinExtrasPrivate bass)
