#include "MusicLibrary/SortLibraryDlg.h"
#include "MusicLibrary/MusicLibrary.h"

#include <QCheckBox>
#include <QDialogButtonBox>
#include <QLabel>
#include <QProgressDialog>
#include <QPushButton>

SortLibraryDlg::SortLibraryDlg(const std::deque<CopyInfo>& cis, ExecuteFileSortCB callback, QWidget* parent)
    : QDialog(parent), m_SortCallback(callback)
{
  for (const CopyInfo& ci : cis)
  {
    m_CopyInfo.push_back(CopyInfo2());
    m_CopyInfo.back().m_Info = ci;
  }

  setupUi(this);

  FillTable();

  ModificationsTable->resizeColumnToContents(0);
  ModificationsTable->resizeColumnToContents(1);
  ModificationsTable->resizeColumnToContents(2);

  ModificationsTable->selectAll();
}

void SortLibraryDlg::on_SortButton_clicked()
{
  RetrieveCheckedState();

  ExecuteFileSort();

  FillTable();
}

void SortLibraryDlg::ExecuteFileSort()
{
  int numChecked = 0;

  for (auto& ci : m_CopyInfo)
  {
    if (ci.m_bMove)
    {
      ++numChecked;
    }
  }

  QProgressDialog progress("Sorting Files", "Cancel", 0, numChecked, this);
  progress.setMinimumDuration(0);
  progress.setWindowModality(Qt::WindowModal);

  std::deque<CopyInfo2> remainingFiles;

  numChecked = 0;
  for (auto& ci : m_CopyInfo)
  {
    if (!ci.m_bMove)
    {
      remainingFiles.push_back(ci);
      continue;
    }

    progress.setValue(++numChecked);

    if (progress.wasCanceled())
      break;

    if (!m_SortCallback(ci.m_Info, ci.m_sErrorMsg))
    {
      remainingFiles.push_back(ci);
    }
  }

  m_CopyInfo.swap(remainingFiles);
}

void SortLibraryDlg::RetrieveCheckedState()
{
  for (int row = 0; row < ModificationsTable->rowCount(); ++row)
  {
    m_CopyInfo[row].m_bMove = false;
  }

  QModelIndexList selection = ModificationsTable->selectionModel()->selectedRows();

  for (QModelIndex idx : selection)
  {
    m_CopyInfo[idx.row()].m_bMove = true;
  }
}

void SortLibraryDlg::FillTable()
{
  ModificationsTable->blockSignals(true);
  ModificationsTable->clear();

  QStringList headers;
  headers.append("Source");
  headers.append("Target");
  headers.append("Message");

  ModificationsTable->setColumnCount(headers.size());
  ModificationsTable->setHorizontalHeaderLabels(headers);

  ModificationsTable->setRowCount((int)m_CopyInfo.size());

  for (int i = 0; i < (int)m_CopyInfo.size(); ++i)
  {
    const auto& ci = m_CopyInfo[i];

    ModificationsTable->setCellWidget(i, 0, new QLabel(ci.m_Info.m_sSource));
    ModificationsTable->setCellWidget(i, 1, new QLabel(ci.m_Info.m_sTargetFile));
    ModificationsTable->setCellWidget(i, 2, new QLabel(ci.m_sErrorMsg));
  }

  ModificationsTable->blockSignals(false);
}
