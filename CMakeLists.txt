cmake_minimum_required(VERSION 3.15)

message("This project can use vcpkg to find its 3rd party dependencies.")
message("You can install vcpkg either manually or using InstallVcpkg.bat.")
message("Make sure to set the environment variable 'VCPKG_ROOT' to point to your installation of vcpkg.")
message("")

message(STATUS "EnvVar %VCPKG_ROOT% is '$ENV{VCPKG_ROOT}'")

if (CMAKE_TOOLCHAIN_FILE)

	message(STATUS "CMAKE_TOOLCHAIN_FILE is already set to '${CMAKE_TOOLCHAIN_FILE}' - not going to modify it.")
	get_filename_component(USED_VCPKG_ROOT "${CMAKE_TOOLCHAIN_FILE}" DIRECTORY)
	get_filename_component(USED_VCPKG_ROOT "${USED_VCPKG_ROOT}" DIRECTORY)
	get_filename_component(USED_VCPKG_ROOT "${USED_VCPKG_ROOT}" DIRECTORY)

else()

	if(DEFINED ENV{VCPKG_ROOT})
		set(USED_VCPKG_ROOT "$ENV{VCPKG_ROOT}")
		message(STATUS "EnvVar VCPKG_ROOT is specified, using that.")
	else()
		set(USED_VCPKG_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/vcpkg")
		message(STATUS "EnvVar VCPKG_ROOT is not specified, using '${CMAKE_CURRENT_SOURCE_DIR}/vcpkg'.")
	endif()

	if(NOT EXISTS "${USED_VCPKG_ROOT}/vcpkg.exe" OR NOT EXISTS "${USED_VCPKG_ROOT}/scripts/buildsystems/vcpkg.cmake")
		message(FATAL_ERROR "vcpkg is not installed. Either install it manually and set the environment variable VCPKG_ROOT to its directory, or run InstallVcpkg.bat")
	endif()

	set(CMAKE_TOOLCHAIN_FILE ${USED_VCPKG_ROOT}/scripts/buildsystems/vcpkg.cmake CACHE STRING "" FORCE)
	message(STATUS "Forcing CMAKE_TOOLCHAIN_FILE to point to '${CMAKE_TOOLCHAIN_FILE}'") 
	
endif()

message(STATUS "USED_VCPKG_ROOT is '${USED_VCPKG_ROOT}'")

set(FORM1_VCPKG_INSTALL_3RD_PARTY_DEPS false CACHE BOOL "")

if (FORM1_VCPKG_INSTALL_3RD_PARTY_DEPS)

	set(VCPKG_INSTALL taglib sqlite3)
	
	foreach(PACKAGE ${VCPKG_INSTALL})

		message("Vcpgk: Installing '${PACKAGE}', this may take a while.")
		execute_process(COMMAND "${USED_VCPKG_ROOT}/vcpkg.exe" install "${PACKAGE}:${VCPKG_TARGET_TRIPLET}" WORKING_DIRECTORY "${USED_VCPKG_ROOT}")

	endforeach()
	
endif()

set(FORM1_VCPKG_INSTALL_QT false CACHE BOOL "")

if (FORM1_VCPKG_INSTALL_QT)

	set(VCPKG_INSTALL qt5-base qt5-winextras)
	
	foreach(PACKAGE ${VCPKG_INSTALL})

		message("Vcpgk: Installing '${PACKAGE}', this may take a while.")
		execute_process(COMMAND "${USED_VCPKG_ROOT}/vcpkg.exe" install "${PACKAGE}:${VCPKG_TARGET_TRIPLET}" WORKING_DIRECTORY "${USED_VCPKG_ROOT}")

	endforeach()
	
endif()




project(Form1 LANGUAGES CXX)

find_package(Qt5 COMPONENTS Widgets Gui Core WinExtras Network REQUIRED)

set (FILES_TO_UI
  "GUI/Form1.ui"
  "Config/SettingsDlg.ui"
  "GUI/SongInfoDlg.ui"
  "GUI/RateSongDlg.ui"
  "Playlists/Smart/SmartPlaylistDlg.ui"
  "Playlists/Radio/RadioPlaylistDlg.ui"
  "MusicLibrary/SortLibraryDlg.ui"
)

set (FILES_TO_MOC
  "GUI/Form1.h"
  "Playlists/Playlist.h"
  "GUI/Sidebar.h"
  "Config/AppState.h"
  "Playlists/AllSongs/AllSongsPlaylist.h"
  "Playlists/Regular/RegularPlaylist.h"
  "Playlists/Smart/SmartPlaylist.h"
  "Playlists/Radio/RadioPlaylist.h"
  "Playlists/Radio/RadioPlaylistDlg.h"
  "MusicLibrary/MusicSource.h"
  "MusicLibrary/MusicSourceFolder.h"
  "MusicLibrary/MusicLibrary.h"
  "Config/AppConfig.h"
  "Config/SettingsDlg.h"
  "GUI/TracklistView.h"
  "GUI/SongInfoDlg.h"
  "GUI/RateSongDlg.h"
  "SoundDevices/SoundDevice.h"
  # "SoundDevices/SoundDeviceQt.h"
  "SoundDevices/SoundDeviceBass.h"
  "Playlists/Smart/SmartPlaylistDlg.h"
  "MusicLibrary/SortLibraryDlg.h"
)

set (FILE_TO_QRC
  "GUI/Form1.qrc"
)

set (OTHER_SOURCES
  "Misc/Main.cpp"
  "GUI/Form1.cpp"
  "MusicLibrary/MusicLibrary.cpp"
  "Playlists/Playlist.cpp"
  "GUI/Sidebar.cpp"
  "GUI/RateSongDlg.cpp"
  "Config/AppState.cpp"
  "Misc/Utils.cpp"
  "Misc/Song.h"
  "Playlists/AllSongs/AllSongsPlaylist.cpp"
  "Playlists/Regular/RegularPlaylist.cpp"
  "Playlists/Smart/SmartPlaylist.cpp"
  "Playlists/Radio/RadioPlaylist.cpp"
  "Playlists/Radio/RadioPlaylistDlg.cpp"
  "MusicLibrary/MusicSource.cpp"
  "MusicLibrary/MusicSourceFolder.cpp"
  "Config/AppConfig.cpp"
  "Config/SettingsDlg.cpp"
  "GUI/TracklistView.cpp"
  "GUI/SongInfoDlg.cpp"
  "Misc/ModificationRecorder.h"
  "Misc/Common.h"
  "Misc/resource.h"
  "Misc/FileSystemWatcher.h"
  "Misc/FileSystemWatcher.cpp"
  "App.rc"
  # "SoundDevices/SoundDeviceQt.cpp"
  "SoundDevices/SoundDeviceBass.cpp"
  "Playlists/Smart/SmartPlaylistDlg.cpp"
  "Playlists/Smart/SmartPlaylistQuery.h"
  "Playlists/Smart/SmartPlaylistQuery.cpp"
  "MusicLibrary/SortLibraryDlg.cpp"
)

include_directories (${CMAKE_BINARY_DIR})
include_directories (${CMAKE_CURRENT_SOURCE_DIR})
link_directories(${CMAKE_CURRENT_SOURCE_DIR})

find_path(TAGLIB_INCLUDE_DIR taglib/tag.h)
find_library(TAGLIB_LIBRARY tag)
include_directories(${TAGLIB_INCLUDE_DIR})

add_executable(${PROJECT_NAME} WIN32)

set_property(GLOBAL PROPERTY USE_FOLDERS ON)

QT5_WRAP_UI(UI_HEADERS ${FILES_TO_UI})
QT5_WRAP_CPP(MOC_FILES TARGET ${PROJECT_NAME} ${FILES_TO_MOC})
QT5_ADD_RESOURCES(QRC_FILES ${FILE_TO_QRC})

target_sources(${PROJECT_NAME} PRIVATE ${UI_HEADERS})
target_sources(${PROJECT_NAME} PRIVATE ${MOC_FILES})
target_sources(${PROJECT_NAME} PRIVATE ${QRC_FILES})

target_sources(${PROJECT_NAME} PRIVATE ${OTHER_SOURCES})
target_sources(${PROJECT_NAME} PRIVATE ${FILE_TO_QRC})
target_sources(${PROJECT_NAME} PRIVATE ${FILES_TO_MOC})
target_sources(${PROJECT_NAME} PRIVATE ${FILES_TO_UI})

source_group (QT\\MOC FILES ${MOC_FILES})
source_group (QT\\UI FILES ${UI_HEADERS})
source_group (QT\\QRC FILES ${QRC_FILES})

set (SRCS ${OTHER_SOURCES} ${FILES_TO_MOC} ${FILES_TO_UI} ${FILE_TO_QRC})

foreach(FILE ${SRCS}) 
    get_filename_component(ABSOLUTE_PATH "${FILE}" ABSOLUTE)

	source_group(TREE "${CMAKE_CURRENT_SOURCE_DIR}" FILES ${ABSOLUTE_PATH})
endforeach()

find_package(SQLite3 REQUIRED)
find_library(SQLITE3_LIBRARY sqlite3)

target_link_libraries(${PROJECT_NAME} ${TAGLIB_LIBRARY})
target_link_libraries(${PROJECT_NAME} ${SQLITE3_LIBRARY})
target_link_libraries(${PROJECT_NAME} Qt5::Widgets Qt5::Core Qt5::Gui Qt5::WinExtras Qt5::WinExtrasPrivate Qt5::Network bass)


add_custom_command(TARGET ${PROJECT_NAME} POST_BUILD
	COMMAND ${CMAKE_COMMAND} -E copy_if_different "${CMAKE_CURRENT_SOURCE_DIR}/bass.dll" $<TARGET_FILE_DIR:${PROJECT_NAME}>
	COMMAND ${CMAKE_COMMAND} -E copy_if_different "${CMAKE_CURRENT_SOURCE_DIR}/basswma.dll" $<TARGET_FILE_DIR:${PROJECT_NAME}>
)