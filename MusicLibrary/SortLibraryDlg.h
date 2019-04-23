#pragma once

#include "Misc/Common.h"
#include "Misc/Song.h"
#include "MusicLibrary/MusicSourceFolder.h"

#include <ui_SortLibraryDlg.h>

#include <QDialog>
#include <deque>
#include <set>

class SortLibraryDlg : public QDialog, Ui_SortLibraryDlg
{
  Q_OBJECT

public:
  SortLibraryDlg(std::deque<CopyInfo>& cis, QWidget* parent);

private slots:

private:
  void FillTable();

  std::deque<CopyInfo>& m_CopyInfo;
};
