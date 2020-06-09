#pragma once

#include "Config/AppState.h"
#include "GUI/Sidebar.h"
#include "Misc/Common.h"
#include <QMainWindow>
#include <QSystemTrayIcon>
#include <ui_Form1.h>

#ifdef Q_OS_WIN32
#include <QWinTaskbarButton>
#endif

class QLocalServer;
class RateSongDlg;

class Form1 : public QMainWindow, Ui_Form1
{
  Q_OBJECT

public:
  Form1();
  ~Form1();

private slots:
  void on_PlayPauseButton_clicked();
  void on_PrevTrackButton_clicked();
  void on_NextTrackButton_clicked();
  void on_VolumeSlider_valueChanged(int newPos);
  void on_TrackPositionSlider_valueChanged(int newPos);
  void on_TracksView_doubleClicked(const QModelIndex& index);
  void on_PlaylistsView_doubleClicked(const QModelIndex& index);
  void on_SettingsButton_clicked();
  void on_SearchLine_textChanged(const QString& text);
  void on_ClearSearchButton_clicked();
  void on_LoopButton_clicked();
  void on_ShuffleButton_clicked();
  void on_ShowSongButton_clicked();

  void onRemoveTracks();
  void onStartCurrentTrack();
  void onVolumeChanged(int newVolume);
  void onNormalizedTrackPositionChanged(double pos);
  void onCurrentSongTimeChanged();
  void onSelectedPlaylistChanged(const QItemSelection& selected, const QItemSelection& deselected);
  void onActiveSongChanged();
  void onPlayingStateChanged();
  void onPlaylistContextMenu(const QPoint& pos);
  void onSidebarContextMenu(const QPoint& pos);
  void onDeletePlaylist();
  void onRenamePlaylist();
  void onAddSelectionToPlaylist();
  void onCreateEmptyPlaylist();
  void onCreateSmartPlaylist();
  void onOpenSongInExplorer();
  void onRefreshPlaylist();
  void onSearchTextChanged(const QString& newText);
  void onLoopShuffleStateChanged();
  void onTrayIconActivated(QSystemTrayIcon::ActivationReason reason);
  void onSingleInstanceActivation();
  void onShowSongInfo();
  void onRefreshSelectedPlaylist();
  void onRateSongs();
  void onRateSong(QString guid, int rating);
  void onBusyWorkActive(bool active);
  void onSaveUserStateTimer();
  void onCopyActionTriggered(bool);
  void onSongRequiresRating(QString guid);

private:
  void ChangeSelectedPlaylist(Playlist* playlist);
  bool RegisterGlobalHotkeys();
  void CreateSystemTrayIcon();
  void StartSingleInstanceServer();
  virtual bool nativeEvent(const QByteArray& eventType, void* message, long* result) override;
  virtual void changeEvent(QEvent* event) override;
  virtual void showEvent(QShowEvent* e) override;

  int m_iSearchDelayCounter = 0;
  QScopedPointer<Sidebar> m_pSidebar;
  Playlist* m_pSelectedPlaylist = nullptr;
  QSystemTrayIcon* m_pSystemTray = nullptr;
  QScopedPointer<QLocalServer> m_LocalInstanceServer;
  QScopedPointer<QAction> m_pTrayPlayPauseAction;
  QScopedPointer<QAction> m_pCopyAction;
  QScopedPointer<RateSongDlg> m_pRateSongDlg;
  bool m_bWasMaximized = false;

#ifdef Q_OS_WIN32
  QScopedPointer<QWinTaskbarButton> m_TaskbarButton;
#endif
};
