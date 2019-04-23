#include "MusicLibrary/SortLibraryDlg.h"
#include "MusicLibrary/MusicLibrary.h"

#include <QCheckBox>
#include <QDialogButtonBox>
#include <QLabel>
#include <QPushButton>

SortLibraryDlg::SortLibraryDlg(std::deque<CopyInfo>& cis, QWidget* parent)
    : QDialog(parent), m_CopyInfo(cis)
{
  setupUi(this);

  QStringList headers;
  headers.append("Move");
  headers.append("Source");
  headers.append("Target");

  ModificationsTable->setHorizontalHeaderLabels(headers);

  FillTable();

  ModificationsTable->resizeColumnToContents(0);
  ModificationsTable->resizeColumnToContents(1);
}

void SortLibraryDlg::FillTable()
{
  ModificationsTable->clear();
  ModificationsTable->blockSignals(true);

  ModificationsTable->setRowCount((int)m_CopyInfo.size());

  for (int i = 0; i < (int)m_CopyInfo.size(); ++i)
  {
    const auto& ci = m_CopyInfo[i];

    QCheckBox* pCheckbox = new QCheckBox();
    pCheckbox->setChecked(ci.m_bMove);

    ModificationsTable->setCellWidget(i, 0, pCheckbox);
    ModificationsTable->setCellWidget(i, 1, new QLabel(ci.m_sSource));
    ModificationsTable->setCellWidget(i, 2, new QLabel(ci.m_sTargetFile));
  }

  ModificationsTable->blockSignals(false);
}
