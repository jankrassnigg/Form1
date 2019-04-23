#pragma once

#include "Misc/Common.h"
#include "Misc/Song.h"

#include <QDialog>
#include <set>

#include <ui_SortLibraryDlg.h>

class SortLibraryDlg : public QDialog, Ui_SortLibraryDlg
{
  Q_OBJECT

public:
  SortLibraryDlg(QWidget* parent);

private slots:

private:
};
