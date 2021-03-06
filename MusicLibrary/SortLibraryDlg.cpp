#include "MusicLibrary/SortLibraryDlg.h"
#include "MusicLibrary/MusicLibrary.h"

#include <QCheckBox>
#include <QDialogButtonBox>
#include <QDir>
#include <QLabel>
#include <QMenu>
#include <QProcess>
#include <QProgressDialog>
#include <QPushButton>
#include <QTableWidget>

SortLibraryDlg::SortLibraryDlg(const QString& folder, const std::deque<CopyInfo>& cis, ExecuteFileSortCB callback, QWidget* parent)
    : QDialog(parent), m_SortCallback(callback), m_sFolder(folder)
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

  ModificationsTable->setContextMenuPolicy(Qt::CustomContextMenu);
  connect(ModificationsTable, &QTableWidget::customContextMenuRequested, this, &SortLibraryDlg::onContextMenu);
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
    {
      remainingFiles.push_back(ci);
      continue;
    }

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

void SortLibraryDlg::onContextMenu(const QPoint& pt)
{
  int rowIdx = ModificationsTable->rowAt(pt.y());

  if (rowIdx < 0)
    return;

  QMenu m(this);
  QAction* pSrc = m.addAction("Open 'Source' in Explorer");
  QAction* pTgt = m.addAction("Open 'Target' in Explorer");

  pSrc->setData(ModificationsTable->item(rowIdx, 0)->toolTip());
  pTgt->setData(ModificationsTable->item(rowIdx, 1)->toolTip());

  connect(pSrc, &QAction::triggered, this, &SortLibraryDlg::onRevealInExplorer);
  connect(pTgt, &QAction::triggered, this, &SortLibraryDlg::onRevealInExplorer);

  m.exec(ModificationsTable->mapToGlobal(pt), pSrc);
}

void SortLibraryDlg::onRevealInExplorer(bool)
{
  QAction* pAction = qobject_cast<QAction*>(sender());
  QString path = pAction->data().toString();

  QStringList args;
  args << "/select,";
  args << QDir::toNativeSeparators(path);
  QProcess::startDetached("explorer", args);
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

  const int chopStart = m_sFolder.length() + 1;

  for (int i = 0; i < (int)m_CopyInfo.size(); ++i)
  {
    const auto& ci = m_CopyInfo[i];
    QTableWidgetItem* pSourceItem = new QTableWidgetItem(ci.m_Info.m_sSource.mid(chopStart));
    QTableWidgetItem* pTargetItem = new QTableWidgetItem(ci.m_Info.m_sTargetFile.mid(chopStart));
    QTableWidgetItem* pMessageItem = new QTableWidgetItem(ci.m_sErrorMsg);
    pSourceItem->setToolTip(ci.m_Info.m_sSource);
    pTargetItem->setToolTip(ci.m_Info.m_sTargetFile);

    if (!ci.m_sErrorMsg.isEmpty())
    {
      pSourceItem->setIcon(QIcon(":/icons/icons/exclamation.png"));
      pMessageItem->setToolTip(ci.m_sErrorMsg);
    }

    ModificationsTable->setItem(i, 0, pSourceItem);
    ModificationsTable->setItem(i, 1, pTargetItem);
    ModificationsTable->setItem(i, 2, pMessageItem);
  }

  ModificationsTable->blockSignals(false);
}
