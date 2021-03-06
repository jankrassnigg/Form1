#pragma once

#include "Misc/Common.h"
#include "Misc/Song.h"
#include "MusicLibrary/MusicSourceFolder.h"

#include <ui_SortLibraryDlg.h>

#include <QDialog>
#include <deque>
#include <set>

typedef bool (*ExecuteFileSortCB)(const CopyInfo&, QString&);

class SortLibraryDlg : public QDialog, Ui_SortLibraryDlg
{
  Q_OBJECT

public:
  SortLibraryDlg(const QString& folder, const std::deque<CopyInfo>& cis, ExecuteFileSortCB callback, QWidget* parent);

private slots:
  void on_SortButton_clicked();

  void ExecuteFileSort();
  void RetrieveCheckedState();
  void onContextMenu(const QPoint& pt);
  void onRevealInExplorer(bool);

private:
  void FillTable();

  ExecuteFileSortCB m_SortCallback;

  struct CopyInfo2
  {
    CopyInfo m_Info;
    bool m_bMove = false;
    QString m_sErrorMsg;
  };

  QString m_sFolder;
  std::deque<CopyInfo2> m_CopyInfo;
};
