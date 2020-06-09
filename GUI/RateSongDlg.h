#pragma once

#include <QDialog>
#include <ui_RateSongDlg.h>

class RateSongDlg : public QDialog, Ui_RateSongDlg
{
  Q_OBJECT

public:
  RateSongDlg();

  void SetSongToRate(const QString& sGuid, const QString& sArtist, const QString& sTitle);

  virtual void reject() override;

signals:
  void SongRated(QString guid, int rating);

private slots:
  void on_Rate0_clicked();
  void on_Rate1_clicked();
  void on_Rate2_clicked();
  void on_Rate3_clicked();
  void on_Rate4_clicked();
  void on_Rate5_clicked();

private:
  QString m_sSongGuid;
};
