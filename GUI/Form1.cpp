#include "GUI/Form1.h"
#include "Config/AppConfig.h"
#include "Config/AppState.h"
#include "Config/SettingsDlg.h"
#include "GUI/SongInfoDlg.h"
#include "MusicLibrary/MusicLibrary.h"
#include "Playlists/Radio/RadioPlaylist.h"
#include "Playlists/Regular/RegularPlaylist.h"
#include "Playlists/Smart/SmartPlaylist.h"
#include "RateSongDlg.h"

#include <QApplication>
#include <QClipboard>
#include <QDir>
#include <QInputDialog>
#include <QItemSelectionModel>
#include <QKeyEvent>
#include <QLocalServer>
#include <QMenu>
#include <QMessageBox>
#include <QMimeData>
#include <QProcess>
#include <QProxyStyle>
#include <QScreen>
#include <QSettings>
#include <QStandardPaths>
#include <QTimer>
#include <QUrl>
#include <QUuid>
#include <set>
#include <windows.h>

// #ifdef Q_OS_WIN32
// #include <QWinTaskbarProgress>
// #endif

class SearchLineEventFilter : public QObject
{
public:
  explicit SearchLineEventFilter(QLineEdit* parent) : QObject(parent) {}

  bool eventFilter(QObject* obj, QEvent* e)
  {
    switch (e->type())
    {
    case QEvent::KeyPress:
    {
      QKeyEvent* keyEvent = static_cast<QKeyEvent*>(e);
      if (keyEvent->key() == Qt::Key_Escape)
      {
        reinterpret_cast<QLineEdit*>(parent())->setText(QString());
      }
      break;
    }
    }

    return QObject::eventFilter(obj, e);
  }
};

class DirectJumpSliderStyle : public QProxyStyle
{
public:
  using QProxyStyle::QProxyStyle;

  int styleHint(QStyle::StyleHint hint, const QStyleOption* option = 0, const QWidget* widget = 0, QStyleHintReturn* returnData = 0) const
  {
    if (hint == QStyle::SH_Slider_AbsoluteSetButtons)
      return (Qt::LeftButton | Qt::MiddleButton | Qt::RightButton);

    return QProxyStyle::styleHint(hint, option, widget, returnData);
  }
};

void Form1::StartSingleInstanceServer()
{
  m_LocalInstanceServer.reset(new QLocalServer);
  m_LocalInstanceServer->setSocketOptions(QLocalServer::WorldAccessOption);
  m_LocalInstanceServer->listen("Form1");
  connect(m_LocalInstanceServer.data(), &QLocalServer::newConnection, this, &Form1::onSingleInstanceActivation);
}

Form1::Form1()
{
  AppState* pAppState = AppState::GetSingleton();
  m_pSidebar.reset(new Sidebar(this));

  setupUi(this);
  CreateSystemTrayIcon();

  // #ifdef Q_OS_WIN32
  //   m_TaskbarButton.reset(new QWinTaskbarButton());
  //   m_TaskbarButton->setWindow(windowHandle());
  // #endif

  MainSplitter->setStretchFactor(0, 1);
  MainSplitter->setStretchFactor(1, 3);

  SearchLine->installEventFilter(new SearchLineEventFilter(SearchLine));

  TracksView->setSelectionMode(QAbstractItemView::SelectionMode::ExtendedSelection);
  TracksView->setSelectionBehavior(QAbstractItemView::SelectionBehavior::SelectRows);
  TracksView->setContextMenuPolicy(Qt::ContextMenuPolicy::CustomContextMenu);
  connect(TracksView, &QWidget::customContextMenuRequested, this, &Form1::onPlaylistContextMenu);
  connect(TracksView, &TrackListView::DeleteItems, this, &Form1::onRemoveTracks);
  connect(TracksView, &TrackListView::StartCurrentItem, this, &Form1::onStartCurrentTrack);

  {
    m_pCopyAction.reset(new QAction("Copy Songs", this));
    m_pCopyAction->setShortcut(QKeySequence("Ctrl+C"));
    connect(m_pCopyAction.data(), &QAction::triggered, this, &Form1::onCopyActionTriggered);

    TracksView->addAction(m_pCopyAction.data());
  }

  {
    m_pEditSongsAction.reset(new QAction("Song Info...", this));
    m_pEditSongsAction->setShortcut(QKeySequence("F2"));
    connect(m_pEditSongsAction.data(), &QAction::triggered, this, &Form1::onShowSongInfo);

    TracksView->addAction(m_pEditSongsAction.data());
  }

  TracksView->setHeaderHidden(false);

  TrackPositionSlider->setStyle(new DirectJumpSliderStyle(this->style()));
  VolumeSlider->setStyle(new DirectJumpSliderStyle(this->style()));
  VolumeSlider->setValue(pAppState->GetVolume());

  connect(pAppState, &AppState::VolumeChanged, this, &Form1::onVolumeChanged);
  connect(pAppState, &AppState::NormalizedTrackPositionChanged, this, &Form1::onNormalizedTrackPositionChanged);
  connect(pAppState, &AppState::CurrentSongTimeChanged, this, &Form1::onCurrentSongTimeChanged);
  connect(pAppState, &AppState::ActiveSongChanged, this, &Form1::onActiveSongChanged);
  connect(pAppState, &AppState::PlayingStateChanged, this, &Form1::onPlayingStateChanged);
  connect(pAppState, &AppState::RefreshSelectedPlaylist, this, &Form1::onRefreshSelectedPlaylist);
  connect(pAppState, &AppState::BusyWorkActive, this, &Form1::onBusyWorkActive, Qt::QueuedConnection);
  connect(pAppState, &AppState::SongRequiresRating, this, &Form1::onSongRequiresRating);

  // start busy indicator
  onBusyWorkActive(pAppState->IsBusyWorkActive());

  connect(MusicLibrary::GetSingleton(), &MusicLibrary::SearchTextChanged, this, &Form1::onSearchTextChanged);

  PlaylistsView->setModel(m_pSidebar.data());
  PlaylistsView->setSelectionMode(QAbstractItemView::SelectionMode::SingleSelection);
  PlaylistsView->setSelectionBehavior(QAbstractItemView::SelectionBehavior::SelectRows);
  PlaylistsView->setContextMenuPolicy(Qt::ContextMenuPolicy::CustomContextMenu);
  PlaylistsView->setAcceptDrops(true);
  PlaylistsView->setDefaultDropAction(Qt::DropAction::CopyAction);
  PlaylistsView->setDropIndicatorShown(true);
  PlaylistsView->setDragDropMode(QAbstractItemView::DropOnly);
  connect(PlaylistsView, &QWidget::customContextMenuRequested, this, &Form1::onSidebarContextMenu);
  connect(PlaylistsView->selectionModel(), &QItemSelectionModel::selectionChanged, this, &Form1::onSelectedPlaylistChanged);

  ChangeSelectedPlaylist(pAppState->GetActivePlaylist());
  onActiveSongChanged();
  onPlayingStateChanged();
  onNormalizedTrackPositionChanged(pAppState->GetNormalizedTrackPosition());

  // restore previous window layout
  {
    QSettings settings;

    settings.beginGroup("TrackView");
    if (settings.contains("ColumnWidth0"))
    {
      const int c0 = settings.value("ColumnWidth0", TracksView->columnWidth(0)).toInt();
      const int c1 = settings.value("ColumnWidth1", TracksView->columnWidth(1)).toInt();
      const int c2 = settings.value("ColumnWidth2", TracksView->columnWidth(2)).toInt();

      TracksView->setColumnWidth(0, Max(20, c0));
      TracksView->setColumnWidth(1, Max(20, c1));
      TracksView->setColumnWidth(2, Max(20, c2));
    }
    settings.endGroup();

    if (settings.contains("MainSplitter0"))
    {
      QList<int> splitSizes;
      splitSizes.push_back(Max(20, settings.value("MainSplitter0", MainSplitter->sizes()[0]).toInt()));
      splitSizes.push_back(Max(20, settings.value("MainSplitter1", MainSplitter->sizes()[1]).toInt()));

      MainSplitter->setSizes(splitSizes);
    }
  }

  StartSingleInstanceServer();
  RegisterGlobalHotkeys();

  m_pRateSongDlg.reset(new RateSongDlg());

  connect(m_pRateSongDlg.get(), &RateSongDlg::SongRated, this, &Form1::onRateSong);

  QTimer::singleShot(1000 * 120, this, SLOT(onSaveUserStateTimer()));
}

Form1::~Form1()
{
  QSettings settings;
  settings.beginGroup("TrackView");
  settings.setValue("ColumnWidth0", TracksView->columnWidth(0));
  settings.setValue("ColumnWidth1", TracksView->columnWidth(1));
  settings.setValue("ColumnWidth2", TracksView->columnWidth(2));
  settings.endGroup();

  settings.setValue("MainSplitter0", MainSplitter->sizes()[0]);
  settings.setValue("MainSplitter1", MainSplitter->sizes()[1]);
}

void Form1::showEvent(QShowEvent* e)
{
  // #ifdef Q_OS_WIN32
  //   m_TaskbarButton->setWindow(windowHandle());
  // #endif

  QMainWindow::showEvent(e);
}

void Form1::on_PlayPauseButton_clicked()
{
  AppState::GetSingleton()->PlayOrPausePlayback();
}

void Form1::on_PrevTrackButton_clicked()
{
  AppState::GetSingleton()->PrevSong();
}

void Form1::on_NextTrackButton_clicked()
{
  AppState::GetSingleton()->NextSong();
}

void Form1::on_VolumeSlider_valueChanged(int newPos)
{
  AppState::GetSingleton()->SetVolume(newPos);
}

void Form1::on_TrackPositionSlider_valueChanged(int newPos)
{
  AppState::GetSingleton()->SetNormalizedTrackPosition(newPos / (double)TrackPositionSlider->maximum());
}

void Form1::onVolumeChanged(int newVolume)
{
  VolumeSlider->blockSignals(true);
  VolumeSlider->setValue(newVolume);
  VolumeSlider->blockSignals(false);
}

void Form1::onNormalizedTrackPositionChanged(double pos)
{
  TrackPositionSlider->blockSignals(true);
  TrackPositionSlider->setValue((int)(TrackPositionSlider->maximum() * pos));
  TrackPositionSlider->blockSignals(false);
}

void Form1::onCurrentSongTimeChanged()
{
  const qint64 duration = AppState::GetSingleton()->GetCurrentSongDuration();

  if (duration == 0)
  {
    TrackTimeLabel->setText(QString());
    return;
  }

  const qint64 position = AppState::GetSingleton()->GetCurrentSongTime();
  const QString time = QString("%1 / %2").arg(ToTime(position)).arg(ToTime(duration));

  TrackTimeLabel->setText(time);
}

void Form1::onActiveSongChanged()
{
  const bool hasSong = !AppState::GetSingleton()->GetActiveSongGuid().isEmpty();

  QString artist = AppState::GetSingleton()->GetCurrentSongArtist();
  QString song = AppState::GetSingleton()->GetCurrentSongTitle();

  ArtistLabel->setText(artist);
  SongLabel->setText(song);

  if (m_pSystemTray)
  {
    m_pSystemTray->setToolTip(hasSong ? (artist + " - " + song) : QString("Form1"));
  }

  ShowSongButton->setEnabled(hasSong);
}

void Form1::onPlayingStateChanged()
{
  switch (AppState::GetSingleton()->GetCurrentPlayingState())
  {
  case AppState::PlayingState::None:
    TrackPositionSlider->setEnabled(false);
    PlayPauseButton->setIcon(QIcon(":/icons/icons/play.png"));
    m_pTrayPlayPauseAction->setText("Play");
    break;
  case AppState::PlayingState::Paused:
    TrackPositionSlider->setEnabled(true);
    PlayPauseButton->setIcon(QIcon(":/icons/icons/play.png"));
    m_pTrayPlayPauseAction->setText("Play");
    break;
  case AppState::PlayingState::Playing:
    TrackPositionSlider->setEnabled(true);
    PlayPauseButton->setIcon(QIcon(":/icons/icons/pause.png"));
    m_pTrayPlayPauseAction->setText("Pause");
    break;
  }
}

void Form1::onPlaylistContextMenu(const QPoint& pos)
{
  if (m_pSelectedPlaylist == nullptr)
    return;

  const QModelIndexList selection = TracksView->selectionModel()->selectedRows();
  if (selection.isEmpty())
    return;

  const bool bSingleSelection = selection.size() == 1;
  QString sSingleGuid;

  if (bSingleSelection)
  {
    sSingleGuid = m_pSelectedPlaylist->GetSongGuid(selection[0].row());
  }

  QMenu menu;

  {
    QMenu* plMenu = menu.addMenu("Add to Playlist");

    {
      QAction* pAdd = plMenu->addAction("New Playlist...");
      pAdd->setData(QVariant::fromValue(static_cast<void*>(nullptr)));
      connect(pAdd, &QAction::triggered, this, &Form1::onAddSelectionToPlaylist);

      plMenu->addSeparator();
    }

    const auto& playlists = AppState::GetSingleton()->GetAllPlaylists();
    for (const auto& pl : playlists)
    {
      if (!pl->CanModifySongList())
        continue;

      QString title = pl->GetTitle();
      title.replace("&", "&&");

      QAction* pAdd = plMenu->addAction(title);
      pAdd->setData(QVariant::fromValue(static_cast<void*>(pl.get())));
      connect(pAdd, &QAction::triggered, this, &Form1::onAddSelectionToPlaylist);

      if (bSingleSelection && pl->ContainsSong(sSingleGuid))
      {
        pAdd->setEnabled(false);
      }
    }
  }

  if (m_pSelectedPlaylist->CanModifySongList())
  {
    connect(menu.addAction("Remove from Playlist"), &QAction::triggered, this, &Form1::onRemoveTracks);
  }

  {
    QMenu* ratingMenu = menu.addMenu("Set Rating");
    connect(ratingMenu->addAction("5 Stars"), &QAction::triggered, this, &Form1::onRateSongs);
    connect(ratingMenu->addAction("4 Stars"), &QAction::triggered, this, &Form1::onRateSongs);
    connect(ratingMenu->addAction("3 Stars"), &QAction::triggered, this, &Form1::onRateSongs);
    connect(ratingMenu->addAction("2 Stars"), &QAction::triggered, this, &Form1::onRateSongs);
    connect(ratingMenu->addAction("1 Star"), &QAction::triggered, this, &Form1::onRateSongs);
    ratingMenu->addSeparator();
    connect(ratingMenu->addAction("No Rating"), &QAction::triggered, this, &Form1::onRateSongs);
  }

  if (bSingleSelection)
  {
    connect(menu.addAction("Open in Explorer"), &QAction::triggered, this, &Form1::onOpenSongInExplorer);
  }

  menu.addAction(m_pCopyAction.data());
  menu.addAction(m_pEditSongsAction.data());

  menu.exec(TracksView->mapToGlobal(pos));
}

void Form1::onSidebarContextMenu(const QPoint& pos)
{
  QMenu menu;
  connect(menu.addAction("New Playlist..."), &QAction::triggered, this, &Form1::onCreateEmptyPlaylist);
  connect(menu.addAction("New Smart Playlist..."), &QAction::triggered, this, &Form1::onCreateSmartPlaylist);
  connect(menu.addAction("New Radio Playlist..."), &QAction::triggered, this, &Form1::onCreateRadioPlaylist);

  const QModelIndexList selection = PlaylistsView->selectionModel()->selectedRows();

  if (m_pSelectedPlaylist != nullptr)
  {
    if (m_pSelectedPlaylist->CanBeRenamed())
    {
      connect(menu.addAction("Rename Playlist..."), &QAction::triggered, this, &Form1::onRenamePlaylist);
    }

    if (m_pSelectedPlaylist->CanBeDeleted())
    {
      connect(menu.addAction("Delete Playlist"), &QAction::triggered, this, &Form1::onDeletePlaylist);
    }

    m_pSelectedPlaylist->ExtendContextMenu(&menu);
  }

  menu.exec(PlaylistsView->mapToGlobal(pos));
}

void Form1::onDeletePlaylist()
{
  if (QMessageBox::question(this, "Delete Playlist?", "Delete the selected playlist?", QMessageBox::Yes | QMessageBox::No, QMessageBox::No) != QMessageBox::Yes)
    return;

  Playlist* toDelete = m_pSelectedPlaylist;

  int prevIndex = m_pSelectedPlaylist->GetPlaylistIndex() - 1;

  ChangeSelectedPlaylist(AppState::GetSingleton()->GetAllPlaylists()[prevIndex].get());

  AppState::GetSingleton()->DeletePlaylist(toDelete);
}

void Form1::onRenamePlaylist()
{
try_again:
  bool ok = false;
  QString name = QInputDialog::getText(this, "Playlist Title", "Name:", QLineEdit::Normal, m_pSelectedPlaylist->GetTitle(), &ok);

  if (!ok)
    return;

  if (name.isEmpty())
    goto try_again;

  if (!AppState::GetSingleton()->RenamePlaylist(m_pSelectedPlaylist, name))
  {
    QMessageBox::information(this, "Playlist Name", "Another playlist already uses this name.\nPlease choose a different name.");
    goto try_again;
  }
}

void Form1::onAddSelectionToPlaylist()
{
  if (m_pSelectedPlaylist == nullptr)
    return;

  const QModelIndexList selection = TracksView->selectionModel()->selectedRows();
  if (selection.isEmpty())
    return;

  QAction* pAction = qobject_cast<QAction*>(sender());
  if (pAction == nullptr)
    return;

  Playlist* pPlaylist = static_cast<Playlist*>(pAction->data().value<void*>());

  std::vector<QString> allSongs;
  allSongs.reserve(selection.size());

  for (auto idx : selection)
  {
    QString guid = m_pSelectedPlaylist->GetSongGuid(idx.row());
    allSongs.push_back(guid);
  }

  if (pPlaylist == nullptr)
  {
    QString nameSuggestion = AppState::GetSingleton()->SuggestPlaylistName(allSongs);

  try_again:
    bool ok = false;
    QString name = QInputDialog::getText(this, "Playlist Title", "Name:", QLineEdit::Normal, nameSuggestion, &ok);

    if (!ok || name.isEmpty())
      return;

    if (AppState::GetSingleton()->FindPlaylistByName(name) != nullptr)
    {
      QMessageBox::information(this, "Playlist Name", "Another playlist already uses this name.\nPlease choose a different name.");
      goto try_again;
    }

    auto pl = std::make_unique<RegularPlaylist>(name, QUuid::createUuid().toString());
    pPlaylist = pl.get();

    AppState::GetSingleton()->AddPlaylist(std::move(pl), true);
  }

  for (const auto& guid : allSongs)
  {
    pPlaylist->AddSong(guid);
  }
}

void Form1::onCreateEmptyPlaylist()
{
try_again:

  bool ok = false;
  QString name = QInputDialog::getText(this, "Playlist Title", "Name:", QLineEdit::Normal, QString(), &ok);

  if (!ok)
    return;

  if (name.isEmpty())
    goto try_again;

  if (AppState::GetSingleton()->FindPlaylistByName(name) != nullptr)
  {
    QMessageBox::information(this, "Playlist Name", "Another playlist already uses this name.\nPlease choose a different name.");
    goto try_again;
  }

  AppState::GetSingleton()->AddPlaylist(std::make_unique<RegularPlaylist>(name, QUuid::createUuid().toString()), true);
}

void Form1::onCreateSmartPlaylist()
{
try_again:

  bool ok = false;
  QString name = QInputDialog::getText(this, "Playlist Title", "Name:", QLineEdit::Normal, QString(), &ok);

  if (!ok)
    return;

  if (name.isEmpty())
    goto try_again;

  if (AppState::GetSingleton()->FindPlaylistByName(name) != nullptr)
  {
    QMessageBox::information(this, "Playlist Name", "Another playlist already uses this name.\nPlease choose a different name.");
    goto try_again;
  }

  AppState::GetSingleton()->AddPlaylist(std::make_unique<SmartPlaylist>(name, QUuid::createUuid().toString()), true);
}

void Form1::onCreateRadioPlaylist()
{
try_again:

  bool ok = false;
  QString name = QInputDialog::getText(this, "Playlist Title", "Name:", QLineEdit::Normal, QString(), &ok);

  if (!ok)
    return;

  if (name.isEmpty())
    goto try_again;

  if (AppState::GetSingleton()->FindPlaylistByName(name) != nullptr)
  {
    QMessageBox::information(this, "Playlist Name", "Another playlist already uses this name.\nPlease choose a different name.");
    goto try_again;
  }

  AppState::GetSingleton()->AddPlaylist(std::make_unique<RadioPlaylist>(name, QUuid::createUuid().toString()), true);
}

void Form1::onOpenSongInExplorer()
{
  if (m_pSelectedPlaylist == nullptr)
    return;

  const QModelIndexList selection = TracksView->selectionModel()->selectedRows();
  if (selection.size() != 1)
    return;

  for (auto idx : selection)
  {
    QString guid = m_pSelectedPlaylist->GetSongGuid(idx.row());

    std::deque<QString> locations;
    MusicLibrary::GetSingleton()->GetSongLocations(guid, locations);

    for (const QString& loc : locations)
    {
      QStringList args;
      args << "/select,";
      args << QDir::toNativeSeparators(loc);
      QProcess::startDetached("explorer", args);
    }
  }
}

void Form1::onSelectedPlaylistChanged(const QItemSelection& selected, const QItemSelection& deselected)
{
  QModelIndexList indices = PlaylistsView->selectionModel()->selectedIndexes();

  if (selected.isEmpty())
    return;

  const QModelIndex idx = indices[0];

  const auto& allLists = AppState::GetSingleton()->GetAllPlaylists();

  const int newIndex = Clamp(idx.row(), 0, (int)allLists.size() - 1);

  ChangeSelectedPlaylist(allLists[newIndex].get());
}

void Form1::ChangeSelectedPlaylist(Playlist* pSelected)
{
  if (m_pSelectedPlaylist == pSelected)
    return;

  if (m_pSelectedPlaylist)
  {
    disconnect(m_pSelectedPlaylist, &Playlist::LoopShuffleStateChanged, this, &Form1::onLoopShuffleStateChanged);
    disconnect(m_pSelectedPlaylist, &Playlist::StatsChanged, this, &Form1::onStatsChanged);
  }

  m_pSelectedPlaylist = pSelected;
  m_pSelectedPlaylist->Refresh(PlaylistRefreshReason::SwitchPlaylist);
  TracksView->sortByColumn(PlaylistColumn::Order, Qt::AscendingOrder);
  TracksView->setSortingEnabled(m_pSelectedPlaylist->CanSort());
  TracksView->setModel(m_pSelectedPlaylist);

  connect(m_pSelectedPlaylist, &Playlist::LoopShuffleStateChanged, this, &Form1::onLoopShuffleStateChanged);
  connect(m_pSelectedPlaylist, &Playlist::StatsChanged, this, &Form1::onStatsChanged);

  QModelIndex index = m_pSidebar->index(pSelected->GetPlaylistIndex(), 0);
  PlaylistsView->blockSignals(true);
  PlaylistsView->selectionModel()->select(index, QItemSelectionModel::SelectionFlag::ClearAndSelect | QItemSelectionModel::SelectionFlag::Rows);
  PlaylistsView->scrollTo(index);
  PlaylistsView->blockSignals(false);

  LoopButton->setEnabled(m_pSelectedPlaylist->CanSelectLoop());
  ShuffleButton->setEnabled(m_pSelectedPlaylist->CanSelectShuffle());

  onLoopShuffleStateChanged();
  onStatsChanged();
}

void Form1::on_TracksView_doubleClicked(const QModelIndex& index)
{
  AppState::GetSingleton()->StartSongFromPlaylist(m_pSelectedPlaylist, index.row());
}

void Form1::on_PlaylistsView_doubleClicked(const QModelIndex& index)
{
  if (m_pSelectedPlaylist)
  {
    AppState::GetSingleton()->StartPlaylist(m_pSelectedPlaylist);
  }
}

void Form1::on_SettingsButton_clicked()
{
  SettingsDlg dlg;
  dlg.exec();
}

void Form1::onRefreshPlaylist()
{
  m_iSearchDelayCounter--;

  // wait until the user stopped typing properly
  if (m_iSearchDelayCounter > 0)
    return;

  QString searchText = SearchLine->text();
  MusicLibrary::GetSingleton()->SetSearchText(searchText);

  if (!searchText.isEmpty())
  {
    // index 0 is the "all songs" playlist
    ChangeSelectedPlaylist(AppState::GetSingleton()->GetAllPlaylists()[0].get());
  }
}

void Form1::onSearchTextChanged(const QString& newText)
{
  m_pSelectedPlaylist->Refresh(PlaylistRefreshReason::SearchChanged);
}

void Form1::onLoopShuffleStateChanged()
{
  if (m_pSelectedPlaylist)
  {
    LoopButton->setChecked(m_pSelectedPlaylist->GetLoop());
    ShuffleButton->setChecked(m_pSelectedPlaylist->GetShuffle());
  }
}

void Form1::onStatsChanged()
{
  if (m_pSelectedPlaylist)
  {
    {
      const int songs = m_pSelectedPlaylist->GetNumSongs();

      QString text = QString("%1 Tracks").arg(songs);
      NumSongsLabel->setText(text);
    }

    const double duration = m_pSelectedPlaylist->GetTotalDuration();

    if (duration == 0)
    {
      PlaylistDurationLabel->setText("");
    }
    else
    {
      QString text = ToTime(static_cast<quint64>(duration * 1000.0));

      PlaylistDurationLabel->setText(text);
    }
  }
}

void Form1::onTrayIconActivated(QSystemTrayIcon::ActivationReason reason)
{
  if (reason == QSystemTrayIcon::ActivationReason::DoubleClick)
  {
    show();

    if (m_bWasMaximized)
    {
      showMaximized();
    }
    else
    {
      showNormal();
    }

    raise();
    activateWindow();
  }
}

void Form1::onSingleInstanceActivation()
{
  show();

  if (m_bWasMaximized)
  {
    showMaximized();
  }
  else
  {
    showNormal();
  }
}

void Form1::onShowSongInfo()
{
  const QModelIndexList selection = TracksView->selectionModel()->selectedRows();
  if (selection.isEmpty())
    return;

  std::set<QString> selectedSongs;

  for (auto idx : selection)
  {
    selectedSongs.insert(m_pSelectedPlaylist->data(idx, Qt::UserRole + 1).toString());
  }

  SongInfoDlg dlg(selectedSongs, this);
  dlg.exec();

  AppState::GetSingleton()->UpdateAdjustedVolume();
}

void Form1::onRefreshSelectedPlaylist()
{
  if (m_pSelectedPlaylist)
  {
    m_pSelectedPlaylist->Refresh(PlaylistRefreshReason::SongInfoChanged);
  }
}

void Form1::onRateSongs()
{
  const QModelIndexList selection = TracksView->selectionModel()->selectedRows();
  if (selection.isEmpty())
    return;

  int iRating = 0;

  const QString title = qobject_cast<QAction*>(sender())->text();

  if (title == "5 Stars")
    iRating = 5;
  else if (title == "4 Stars")
    iRating = 4;
  else if (title == "3 Stars")
    iRating = 3;
  else if (title == "2 Stars")
    iRating = 2;
  else if (title == "1 Star")
    iRating = 1;

  for (auto idx : selection)
  {
    QString sGuid = m_pSelectedPlaylist->GetSongGuid(idx.row());

    MusicLibrary::GetSingleton()->UpdateSongRating(sGuid, iRating, true);
  }
}

void Form1::onRateSong(QString guid, int rating, bool skipAfterRating)
{
  MusicLibrary::GetSingleton()->UpdateSongRating(guid, rating, true);

  if (!m_pRateSongDlg->isHidden() && m_pRateSongDlg->GetSkipSongAfterRating())
  {
    skipAfterRating = true;
  }

  if (AppState::GetSingleton()->GetActiveSongGuid() == guid)
  {
    AppState::GetSingleton()->CountCurrentSongAsPlayed();

    if (rating == 1 || skipAfterRating)
    {
      AppState::GetSingleton()->NextSong();
    }
  }

  m_pRateSongDlg->hide();
}

void Form1::onBusyWorkActive(bool active)
{
  BusyProgress->setVisible(active);

  // #ifdef Q_OS_WIN32
  //   QWinTaskbarProgress* progress = m_TaskbarButton->progress();
  //   progress->setVisible(active);
  //   progress->setRange(0, 0);
  //   progress->setValue(0);
  // #endif
}

void Form1::onSaveUserStateTimer()
{
  const QString sAppDir = QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation);

  AppConfig::GetSingleton()->Save(sAppDir);
  MusicLibrary::GetSingleton()->SaveUserState();
  AppState::GetSingleton()->SaveAllPlaylists(false);
  AppState::GetSingleton()->SaveUserState();

  QTimer::singleShot(1000 * 60, this, SLOT(onSaveUserStateTimer()));
}

void Form1::on_SearchLine_textChanged(const QString& text)
{
  ClearSearchButton->setEnabled(!text.isEmpty());

  // delay retrieving the text
  m_iSearchDelayCounter++;
  QTimer::singleShot(450, this, &Form1::onRefreshPlaylist);
}

void Form1::on_ClearSearchButton_clicked()
{
  SearchLine->setText(QString());
  SearchLine->setFocus();
}

void Form1::on_LoopButton_clicked()
{
  if (m_pSelectedPlaylist)
  {
    m_pSelectedPlaylist->SetLoop(!m_pSelectedPlaylist->GetLoop());
  }
}

void Form1::on_ShuffleButton_clicked()
{
  if (m_pSelectedPlaylist)
  {
    m_pSelectedPlaylist->SetShuffle(!m_pSelectedPlaylist->GetShuffle());
  }
}

void Form1::on_ShowSongButton_clicked()
{
  Playlist* pl = AppState::GetSingleton()->GetActivePlaylist();
  if (!pl)
    return;

  ChangeSelectedPlaylist(pl);

  const int songIdx = pl->GetActiveSong();
  if (songIdx < 0)
    return;

  QModelIndex idx = pl->index(songIdx, 0);
  TracksView->setFocus();
  TracksView->scrollTo(idx);
  TracksView->selectionModel()->select(idx, QItemSelectionModel::SelectionFlag::ClearAndSelect | QItemSelectionModel::SelectionFlag::Rows);
  TracksView->setCurrentIndex(idx);
}

void Form1::onRemoveTracks()
{
  if (!m_pSelectedPlaylist->CanModifySongList())
    return;

  const QModelIndexList selection = TracksView->selectionModel()->selectedRows();
  if (selection.isEmpty())
    return;

  std::vector<int> sortedIndices;
  sortedIndices.reserve(selection.size());

  for (auto idx : selection)
  {
    sortedIndices.push_back(idx.row());
  }

  sort(sortedIndices.begin(), sortedIndices.end());

  for (size_t i = sortedIndices.size(); i > 0; --i)
  {
    m_pSelectedPlaylist->RemoveSong(sortedIndices[i - 1]);
  }

  // TODO: use better signal
  m_pSelectedPlaylist->Refresh(PlaylistRefreshReason::TrackRemoved);
}

void Form1::onStartCurrentTrack()
{
  QModelIndex index = TracksView->currentIndex();

  if (!index.isValid())
    return;

  AppState::GetSingleton()->StartSongFromPlaylist(m_pSelectedPlaylist, index.row());

  TracksView->setCurrentIndex(index);
  TracksView->setFocus();
}

bool Form1::RegisterGlobalHotkeys()
{
  if (!RegisterHotKey(HWND(winId()), 1, MOD_WIN, VK_NUMPAD5))
    return false;
  if (!RegisterHotKey(HWND(winId()), 1, MOD_WIN, VK_NUMPAD4))
    return false;
  if (!RegisterHotKey(HWND(winId()), 1, MOD_WIN, VK_NUMPAD6))
    return false;
  if (!RegisterHotKey(HWND(winId()), 1, MOD_WIN, VK_NUMPAD8))
    return false;
  if (!RegisterHotKey(HWND(winId()), 1, MOD_WIN, VK_NUMPAD2))
    return false;
  if (!RegisterHotKey(HWND(winId()), 1, MOD_WIN, VK_NUMPAD1))
    return false;
  if (!RegisterHotKey(HWND(winId()), 1, MOD_WIN, VK_NUMPAD3))
    return false;
  if (!RegisterHotKey(HWND(winId()), 2, MOD_WIN | MOD_CONTROL, VK_NUMPAD0))
    return false;
  if (!RegisterHotKey(HWND(winId()), 2, MOD_WIN | MOD_CONTROL, VK_NUMPAD1))
    return false;
  if (!RegisterHotKey(HWND(winId()), 2, MOD_WIN | MOD_CONTROL, VK_NUMPAD2))
    return false;
  if (!RegisterHotKey(HWND(winId()), 2, MOD_WIN | MOD_CONTROL, VK_NUMPAD3))
    return false;
  if (!RegisterHotKey(HWND(winId()), 2, MOD_WIN | MOD_CONTROL, VK_NUMPAD4))
    return false;
  if (!RegisterHotKey(HWND(winId()), 2, MOD_WIN | MOD_CONTROL, VK_NUMPAD5))
    return false;
  if (!RegisterHotKey(HWND(winId()), 2, MOD_WIN | MOD_CONTROL, VK_NUMPAD9))
    return false;

  return true;
}

bool Form1::nativeEvent(const QByteArray& eventType, void* message, qintptr* result)
{
  Q_UNUSED(eventType);
  Q_UNUSED(result);

  MSG* msg = static_cast<MSG*>(message);

  if (msg->message == WM_HOTKEY)
  {
    MSG* msg = reinterpret_cast<MSG*>(message);

    if (LOWORD(msg->lParam) == MOD_WIN && HIWORD(msg->lParam) == VK_NUMPAD5)
    {
      AppState::GetSingleton()->PlayOrPausePlayback();
    }
    else if (LOWORD(msg->lParam) == MOD_WIN && HIWORD(msg->lParam) == VK_NUMPAD6)
    {
      AppState::GetSingleton()->NextSong();
    }
    else if (LOWORD(msg->lParam) == MOD_WIN && HIWORD(msg->lParam) == VK_NUMPAD4)
    {
      AppState::GetSingleton()->PrevSong();
    }
    else if (LOWORD(msg->lParam) == MOD_WIN && HIWORD(msg->lParam) == VK_NUMPAD8)
    {
      AppState::GetSingleton()->SetVolume(AppState::GetSingleton()->GetVolume() + 5);
    }
    else if (LOWORD(msg->lParam) == MOD_WIN && HIWORD(msg->lParam) == VK_NUMPAD2)
    {
      AppState::GetSingleton()->SetVolume(AppState::GetSingleton()->GetVolume() - 5);
    }
    else if (LOWORD(msg->lParam) == MOD_WIN && HIWORD(msg->lParam) == VK_NUMPAD1)
    {
      AppState::GetSingleton()->FastForward(-10);
    }
    else if (LOWORD(msg->lParam) == MOD_WIN && HIWORD(msg->lParam) == VK_NUMPAD3)
    {
      AppState::GetSingleton()->FastForward(+10);
    }
    else if (LOWORD(msg->lParam) == (MOD_WIN | MOD_CONTROL) && HIWORD(msg->lParam) == VK_NUMPAD0)
    {
      onRateSong(AppState::GetSingleton()->GetActiveSongGuid(), 0, false);
    }
    else if (LOWORD(msg->lParam) == (MOD_WIN | MOD_CONTROL) && HIWORD(msg->lParam) == VK_NUMPAD1)
    {
      onRateSong(AppState::GetSingleton()->GetActiveSongGuid(), 1, false);
    }
    else if (LOWORD(msg->lParam) == (MOD_WIN | MOD_CONTROL) && HIWORD(msg->lParam) == VK_NUMPAD2)
    {
      onRateSong(AppState::GetSingleton()->GetActiveSongGuid(), 2, false);
    }
    else if (LOWORD(msg->lParam) == (MOD_WIN | MOD_CONTROL) && HIWORD(msg->lParam) == VK_NUMPAD3)
    {
      onRateSong(AppState::GetSingleton()->GetActiveSongGuid(), 3, false);
    }
    else if (LOWORD(msg->lParam) == (MOD_WIN | MOD_CONTROL) && HIWORD(msg->lParam) == VK_NUMPAD4)
    {
      onRateSong(AppState::GetSingleton()->GetActiveSongGuid(), 4, false);
    }
    else if (LOWORD(msg->lParam) == (MOD_WIN | MOD_CONTROL) && HIWORD(msg->lParam) == VK_NUMPAD5)
    {
      onRateSong(AppState::GetSingleton()->GetActiveSongGuid(), 5, false);
    }
    else if (LOWORD(msg->lParam) == (MOD_WIN | MOD_CONTROL) && HIWORD(msg->lParam) == VK_NUMPAD9)
    {
      RateAndSkipSong(AppState::GetSingleton()->GetActiveSongGuid());
    }
  }

  return QMainWindow::nativeEvent(eventType, message, result);
}

void Form1::changeEvent(QEvent* event)
{
  if (event->type() == QEvent::WindowStateChange)
  {
    if (isMinimized())
    {
      m_bWasMaximized = (((QWindowStateChangeEvent*)event)->oldState() == Qt::WindowState::WindowMaximized);

      hide();
    }
  }

  QMainWindow::changeEvent(event);
}

void Form1::CreateSystemTrayIcon()
{
  if (m_pSystemTray == nullptr)
  {
    m_pSystemTray = new QSystemTrayIcon(this);
    m_pSystemTray->setIcon(QIcon(":/icons/Form1.png"));
    m_pSystemTray->setToolTip("Form1");
    m_pSystemTray->show();

    m_pSystemTray->setContextMenu(new QMenu());
    m_pTrayPlayPauseAction.reset(m_pSystemTray->contextMenu()->addAction("Play"));
    connect(m_pTrayPlayPauseAction.data(), &QAction::triggered, this, [this]()
            { on_PlayPauseButton_clicked(); });
    connect(m_pSystemTray->contextMenu()->addAction("Next Song"), &QAction::triggered, this, [this]()
            { on_NextTrackButton_clicked(); });
    connect(m_pSystemTray->contextMenu()->addAction("Quit"), &QAction::triggered, this, [this]()
            { close(); });

    connect(m_pSystemTray, &QSystemTrayIcon::activated, this, &Form1::onTrayIconActivated);
  }
}

void Form1::onCopyActionTriggered(bool)
{
  QModelIndexList indexes = TracksView->selectionModel()->selectedRows(0);

  std::deque<QString> locations;
  MusicLibrary* ml = MusicLibrary::GetSingleton();

  QList<QUrl> fileUrls;

  for (QModelIndex idx : indexes)
  {
    QString songGuid = TracksView->model()->data(idx, Qt::UserRole + 1).toString();

    ml->GetSongLocations(songGuid, locations);

    for (const QString& path : locations)
    {
      if (QFile::exists(path))
      {
        fileUrls.append(QUrl::fromLocalFile(path));
        break;
      }
    }
  }

  if (fileUrls.isEmpty())
    return;

  QMimeData* mimeData = new QMimeData();
  mimeData->setUrls(fileUrls);
  QGuiApplication::clipboard()->setMimeData(mimeData);
}

void Form1::onSongRequiresRating(QString guid)
{
  if (!AppConfig::GetSingleton()->GetShowRateSongPopup())
    return;

  ShowSongRatingDialog(guid, false);
}

bool Form1::ShowSongRatingDialog(QString guid, bool skipAfterRating)
{
  SongInfo info;
  MusicLibrary::GetSingleton()->FindSong(guid, info);

  if (info.m_iRating != 0)
    return false;

  m_pRateSongDlg->setWindowFlag(Qt::WindowType::WindowStaysOnTopHint, true);
  m_pRateSongDlg->SetSongToRate(guid, info.m_sArtist, info.m_sTitle, skipAfterRating);

  const QRect screen = QApplication::primaryScreen()->availableGeometry();
  m_pRateSongDlg->updateGeometry();
  const QRect dlgGeo = m_pRateSongDlg->geometry();

  QPoint pos = screen.bottomRight();
  pos.setX(pos.x() - dlgGeo.size().width());
  pos.setY(pos.y() - dlgGeo.size().height() - 35);

  m_pRateSongDlg->move(pos);
  m_pRateSongDlg->show();

  return true;
}

void Form1::RateAndSkipSong(QString guid)
{
  AppState::GetSingleton()->CountCurrentSongAsPlayed();

  if (!ShowSongRatingDialog(guid, true))
  {
    AppState::GetSingleton()->NextSong();
  }
}
