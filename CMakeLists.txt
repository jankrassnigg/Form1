cmake_minimum_required(VERSION 3.14)

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
  # "SoundDeviceQt.h"
  "SoundDeviceBass.h"
  "SmartPlaylistDlg.h"
)

set (FILE_TO_QRC
  "Form1.qrc"
)

include_directories (${CMAKE_BINARY_DIR})
link_directories(${CMAKE_CURRENT_SOURCE_DIR})

find_path(TAGLIB_INCLUDE_DIR taglib/tag.h)
find_library(TAGLIB_LIBRARY tag)
include_directories(${TAGLIB_INCLUDE_DIR})

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
  # "SoundDeviceQt.cpp"
  "SoundDeviceBass.cpp"
  "SmartPlaylistDlg.cpp"
  "SmartPlaylistQuery.h"
  "SmartPlaylistQuery.cpp"
  ${FILE_TO_QRC}
  ${FILES_TO_MOC}
  ${FILES_TO_UI}
)

QT5_WRAP_UI(UI_HEADERS ${FILES_TO_UI})
QT5_WRAP_CPP(MOC_FILES TARGET ${PROJECT_NAME} ${FILES_TO_MOC})
QT5_ADD_RESOURCES(QRC_FILES ${FILE_TO_QRC})

target_sources(${PROJECT_NAME} PRIVATE ${UI_HEADERS})
target_sources(${PROJECT_NAME} PRIVATE ${MOC_FILES})
target_sources(${PROJECT_NAME} PRIVATE ${QRC_FILES})

source_group (QT\\MOC FILES ${MOC_FILES})
source_group (QT\\UI FILES ${UI_HEADERS})
source_group (QT FILES ${FILES_TO_UI})
source_group (QT\\QRC FILES ${FILE_TO_QRC} ${QRC_FILES})

find_package(Sqlite3 REQUIRED)
find_library(SQLITE3_LIBRARY sqlite3)

target_link_libraries(${PROJECT_NAME} ${TAGLIB_LIBRARY})
target_link_libraries(${PROJECT_NAME} ${SQLITE3_LIBRARY})
target_link_libraries(${PROJECT_NAME} Qt5::Widgets Qt5::Core Qt5::Gui Qt5::WinExtras Qt5::WinExtrasPrivate Qt5::Network bass)


add_custom_command(TARGET ${PROJECT_NAME} POST_BUILD
	COMMAND ${CMAKE_COMMAND} -E copy_if_different "${CMAKE_CURRENT_SOURCE_DIR}/bass.dll" $<TARGET_FILE_DIR:${PROJECT_NAME}>
	COMMAND ${CMAKE_COMMAND} -E copy_if_different "${CMAKE_CURRENT_SOURCE_DIR}/basswma.dll" $<TARGET_FILE_DIR:${PROJECT_NAME}>
)