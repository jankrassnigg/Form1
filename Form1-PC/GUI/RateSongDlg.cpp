#include "GUI/RateSongDlg.h"

 RateSongDlg::RateSongDlg()
    : QDialog(nullptr)
{
  setupUi(this);
}

void RateSongDlg::SetSongToRate(const QString& sGuid, const QString& sArtist, const QString& sTitle, bool skipAfterRating)
{
  if (m_sSongGuid == sGuid)
  {
    m_bSkipAfterRating = m_bSkipAfterRating || skipAfterRating;
  }
  else
  {
    m_bSkipAfterRating = skipAfterRating;
  }

  m_sSongGuid = sGuid;

  Artist->setText(sArtist);
  Title->setText(sTitle);
}

void RateSongDlg::reject()
{
  on_Rate0_clicked();
}

void RateSongDlg::on_Rate0_clicked()
{
  hide();
}

void RateSongDlg::on_Rate1_clicked()
{
  emit SongRated(m_sSongGuid, 1, m_bSkipAfterRating);
  hide();
}

void RateSongDlg::on_Rate2_clicked()
{
  emit SongRated(m_sSongGuid, 2, m_bSkipAfterRating);
  hide();
}

void RateSongDlg::on_Rate3_clicked()
{
  emit SongRated(m_sSongGuid, 3, m_bSkipAfterRating);
  hide();
}

void RateSongDlg::on_Rate4_clicked()
{
  emit SongRated(m_sSongGuid, 4, m_bSkipAfterRating);
  hide();
}

void RateSongDlg::on_Rate5_clicked()
{
  emit SongRated(m_sSongGuid, 5, m_bSkipAfterRating);
  hide();
}

