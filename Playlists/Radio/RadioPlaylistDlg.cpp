#include "RadioPlaylistDlg.h"
#include "Config/AppState.h"
#include "RadioPlaylist.h"
#include <QComboBox>
#include <QDialogButtonBox>
#include <QLineEdit>
#include <QPushButton>

RadioPlaylistDlg::RadioPlaylistDlg(RadioPlaylist* playlist, QWidget* parent)
    : QDialog(parent)
{
  m_Playlist = playlist;

  setupUi(this);

  UsePlaylist1->setChecked(true);

  const auto& lists = AppState::GetSingleton()->GetAllPlaylists();

  Playlist1->addItem("<none>", "");
  Playlist2->addItem("<none>", "");
  Playlist3->addItem("<none>", "");
  Playlist4->addItem("<none>", "");
  Playlist5->addItem("<none>", "");

  for (const auto& pList : lists)
  {
    if (pList->GetCategory() == PlaylistCategory::Radio)
      continue;

    Playlist1->addItem(pList->GetIcon(), pList->GetTitle(), pList->GetGuid());
    Playlist2->addItem(pList->GetIcon(), pList->GetTitle(), pList->GetGuid());
    Playlist3->addItem(pList->GetIcon(), pList->GetTitle(), pList->GetGuid());
    Playlist4->addItem(pList->GetIcon(), pList->GetTitle(), pList->GetGuid());
    Playlist5->addItem(pList->GetIcon(), pList->GetTitle(), pList->GetGuid());
  }

  Percentage1->setValue(100);
  Percentage2->setValue(20);
  Percentage3->setValue(10);
  Percentage4->setValue(10);
  Percentage5->setValue(10);

  QSpinBox* Spinboxes[5] = {Percentage1, Percentage2, Percentage3, Percentage4, Percentage5};
  QComboBox* Combos[5] = {Playlist1, Playlist2, Playlist3, Playlist4, Playlist5};
  QCheckBox* Checks[5] = {UsePlaylist1, UsePlaylist2, UsePlaylist3, UsePlaylist4, UsePlaylist5};

  for (size_t i = 0; i < playlist->m_Settings.m_Items.size(); ++i)
  {
    if (i >= 5)
      break;

    const auto& item = playlist->m_Settings.m_Items[i];

    Checks[i]->setChecked(item.m_bEnabled);
    Spinboxes[i]->setValue(item.m_iLikelyhood);

    int idx = Combos[i]->findData(item.m_sPlaylistGuid);
    if (idx >= 0)
    {
      Combos[i]->setCurrentIndex(idx);
    }
    else
    {
      Combos[i]->setCurrentIndex(0);
    }
  }
}

void RadioPlaylistDlg::on_UsePlaylist1_toggled()
{
  Playlist1->setEnabled(UsePlaylist1->isChecked());
  Percentage1->setEnabled(UsePlaylist1->isChecked());
}

void RadioPlaylistDlg::on_UsePlaylist2_toggled()
{
  Playlist2->setEnabled(UsePlaylist2->isChecked());
  Percentage2->setEnabled(UsePlaylist2->isChecked());
}

void RadioPlaylistDlg::on_UsePlaylist3_toggled()
{
  Playlist3->setEnabled(UsePlaylist3->isChecked());
  Percentage3->setEnabled(UsePlaylist3->isChecked());
}

void RadioPlaylistDlg::on_UsePlaylist4_toggled()
{
  Playlist4->setEnabled(UsePlaylist4->isChecked());
  Percentage4->setEnabled(UsePlaylist4->isChecked());
}

void RadioPlaylistDlg::on_UsePlaylist5_toggled()
{
  Playlist5->setEnabled(UsePlaylist5->isChecked());
  Percentage5->setEnabled(UsePlaylist5->isChecked());
}

void RadioPlaylistDlg::on_Buttons_clicked(QAbstractButton* pButton)
{
  if (pButton == Buttons->button(QDialogButtonBox::StandardButton::Ok))
  {
    QSpinBox* Spinboxes[5] = {Percentage1, Percentage2, Percentage3, Percentage4, Percentage5};
    QComboBox* Combos[5] = {Playlist1, Playlist2, Playlist3, Playlist4, Playlist5};
    QCheckBox* Checks[5] = {UsePlaylist1, UsePlaylist2, UsePlaylist3, UsePlaylist4, UsePlaylist5};

    m_Playlist->m_Settings.m_Items.resize(5);

    for (size_t i = 0; i < m_Playlist->m_Settings.m_Items.size(); ++i)
    {
      auto& item = m_Playlist->m_Settings.m_Items[i];

      item.m_bEnabled = Checks[i]->isChecked();
      item.m_sPlaylistGuid = Combos[i]->currentData().toString();
      item.m_iLikelyhood = Spinboxes[i]->value();
    }

    m_Playlist->SetModified();

    RadioPlaylistModification mod;
    mod.m_Settings = m_Playlist->m_Settings;
    mod.m_Type = RadioPlaylistModification::Type::ChangeSettings;

    m_Playlist->m_Recorder.AddModification(mod, m_Playlist);

    m_Playlist->Refresh(PlaylistRefreshReason::PlaylistModified);

    accept();
    return;
  }

  if (pButton == Buttons->button(QDialogButtonBox::StandardButton::Cancel))
  {
    reject();
    return;
  }
}
