#include "RadioPlaylistDlg.h"
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
}
