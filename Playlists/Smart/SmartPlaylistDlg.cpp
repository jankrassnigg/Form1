#include "SmartPlaylistDlg.h"
#include "SmartPlaylist.h"
#include <QComboBox>
#include <QDialogButtonBox>
#include <QLineEdit>
#include <QPushButton>

SmartPlaylistDlg::SmartPlaylistDlg(SmartPlaylist* playlist, QWidget* parent)
    : QDialog(parent)
{
  m_Playlist = playlist;

  setupUi(this);

  StatementsArea->setLayout(new QVBoxLayout());
  StatementsArea->setWidgetResizable(true);

  m_Query = playlist->m_Query;

  for (int i = 0; i < (int)SmartPlaylistQuery::SortOrder::ENUM_COUNT; ++i)
  {
    SortPlaylistCombo->addItem(SmartPlaylistQuery::ToUiString((SmartPlaylistQuery::SortOrder)i));
  }

  SortPlaylistCombo->setCurrentIndex((int)m_Query.m_SortOrder);
  SongLimitSpinbox->setValue(m_Query.m_iSongLimit);

  RebuildUI();
}

void SmartPlaylistDlg::RebuildUI()
{
  if (StatementsArea->widget() != nullptr)
  {
    QWidget* pWidget = StatementsArea->widget();
    StatementsArea->setWidget(nullptr);
    delete pWidget;
  }

  StatementsArea->setFrameShape(QFrame::Shape::NoFrame);
  StatementsArea->setContentsMargins(0, 0, 0, 0);

  QWidget* pInnerWidget = new QWidget(StatementsArea);
  pInnerWidget->setLayout(new QVBoxLayout());
  pInnerWidget->setContentsMargins(0, 0, 0, 0);

  AddGroupUI(nullptr, &m_Query.m_MainGroup, pInnerWidget);

  pInnerWidget->layout()->addItem(new QSpacerItem(0, 0, QSizePolicy::Minimum, QSizePolicy::Expanding));

  StatementsArea->setWidget(pInnerWidget);
}

QWidget* SmartPlaylistDlg::AddGroupUI(SmartPlaylistQuery::ConditionGroup* pParentGroup, SmartPlaylistQuery::ConditionGroup* pGroup, QWidget* pParentWidget)
{
  QFrame* pGroupWidget = new QFrame(pParentWidget);
  pGroupWidget->setLayout(new QVBoxLayout());
  pGroupWidget->setFrameShape(pParentGroup != nullptr ? QFrame::Shape::StyledPanel : QFrame::Shape::NoFrame);
  pGroupWidget->setFrameShape(QFrame::Shape::StyledPanel);
  pGroupWidget->setContentsMargins(1, 1, 1, 1);

  {
    QWidget* pHeader = new QWidget(pGroupWidget);
    pHeader->setLayout(new QHBoxLayout());
    pHeader->layout()->setContentsMargins(0, 0, 0, 0);
    pHeader->setContentsMargins(0, 0, 0, 0);

    if (pParentGroup != nullptr)
    {
      QPushButton* pRemoveGroupButton = new QPushButton(pGroupWidget);
      pRemoveGroupButton->setText(QString());
      pRemoveGroupButton->setIcon(QIcon(":/icons/icons/remove_group.png"));
      pRemoveGroupButton->setToolTip("Remove Group");
      pRemoveGroupButton->setProperty("group", QVariant::fromValue<void*>(pGroup));
      pRemoveGroupButton->setProperty("parentgroup", QVariant::fromValue<void*>(pParentGroup));
      connect(pRemoveGroupButton, &QPushButton::clicked, this, &SmartPlaylistDlg::onRemoveGroupClicked);

      pHeader->layout()->addWidget(pRemoveGroupButton);
    }

    {
      QComboBox* pFulfilBox = new QComboBox(pGroupWidget);
      pFulfilBox->addItem("all");
      pFulfilBox->addItem("any");
      pFulfilBox->setCurrentIndex((int)pGroup->m_Fulfil);
      pFulfilBox->setProperty("group", QVariant::fromValue<void*>(pGroup));
      pFulfilBox->setToolTip("How many statements in this group must be fulfilled.");
      connect(pFulfilBox, SIGNAL(currentIndexChanged(int)), this, SLOT(onFulfilChanged(int)));

      pHeader->layout()->addWidget(pFulfilBox);
    }

    pHeader->layout()->addItem(new QSpacerItem(0, 0, QSizePolicy::Expanding, QSizePolicy::Minimum));

    {
      QPushButton* pAddGroupButton = new QPushButton(pGroupWidget);
      pAddGroupButton->setText(QString());
      pAddGroupButton->setIcon(QIcon(":/icons/icons/add_group.png"));
      pAddGroupButton->setProperty("group", QVariant::fromValue<void*>(pGroup));
      pAddGroupButton->setToolTip("Add Group");
      connect(pAddGroupButton, &QPushButton::clicked, this, &SmartPlaylistDlg::onAddGroupClicked);

      pHeader->layout()->addWidget(pAddGroupButton);
    }

    {
      QPushButton* pAddStmtButton = new QPushButton(pGroupWidget);
      pAddStmtButton->setText(QString());
      pAddStmtButton->setIcon(QIcon(":/icons/icons/add_statement.png"));
      pAddStmtButton->setProperty("group", QVariant::fromValue<void*>(pGroup));
      pAddStmtButton->setToolTip("Add Statement");
      connect(pAddStmtButton, &QPushButton::clicked, this, &SmartPlaylistDlg::onAddStatementClicked);

      pHeader->layout()->addWidget(pAddStmtButton);
    }

    pGroupWidget->layout()->addWidget(pHeader);
  }

  for (auto& stmt : pGroup->m_Statements)
  {
    AddStatementUI(pGroup, &stmt, pGroupWidget);
  }

  for (auto& sub : pGroup->m_SubGroups)
  {
    AddGroupUI(pGroup, &sub, pGroupWidget);
  }

  pParentWidget->layout()->addWidget(pGroupWidget);

  return pGroupWidget;
}

void SmartPlaylistDlg::AddStatementUI(SmartPlaylistQuery::ConditionGroup* pGroup, SmartPlaylistQuery::Statement* pStmt, QWidget* pParentWidget)
{
  QWidget* pContainer = new QWidget(pParentWidget);
  pContainer->setLayout(new QHBoxLayout());
  pContainer->layout()->setContentsMargins(0, 0, 0, 0);

  pParentWidget->layout()->addWidget(pContainer);

  {
    QPushButton* pRemove = new QPushButton(pContainer);
    pRemove->setObjectName("Remove");
    pRemove->setProperty("stmt", QVariant::fromValue<void*>(pStmt));
    pRemove->setProperty("group", QVariant::fromValue<void*>(pGroup));
    pRemove->setIcon(QIcon(":/icons/icons/remove_statement.png"));
    pRemove->setText(QString());
    pRemove->setToolTip("Remove Statement");
    connect(pRemove, &QPushButton::clicked, this, &SmartPlaylistDlg::onRemoveSmtClicked);

    pContainer->layout()->addWidget(pRemove);
  }

  {
    QComboBox* pCriterium = new QComboBox(pContainer);
    pCriterium->setObjectName("Criterium");
    pCriterium->setProperty("stmt", QVariant::fromValue<void*>(pStmt));
    FillCriteriums(pCriterium);
    pCriterium->setCurrentIndex((int)pStmt->m_Criterium);
    pCriterium->setToolTip("The criterion to check");
    connect(pCriterium, SIGNAL(currentIndexChanged(int)), this, SLOT(onCriteriumChanged(int)));

    pContainer->layout()->addWidget(pCriterium);
  }

  {
    QComboBox* pCompare = new QComboBox(pContainer);
    pCompare->setObjectName("Compare");
    pCompare->setProperty("stmt", QVariant::fromValue<void*>(pStmt));
    FillConditions(pCompare, pStmt->m_Criterium);
    pCompare->setCurrentText(SmartPlaylistQuery::ToUiString(pStmt->m_Compare));
    pCompare->setToolTip("How to compare the criterion with the value");
    connect(pCompare, SIGNAL(currentIndexChanged(int)), this, SLOT(onCompareChanged(int)));

    pContainer->layout()->addWidget(pCompare);
  }

  {
    QLineEdit* pValue = new QLineEdit(pContainer);
    pValue->setObjectName("Value");
    pValue->setProperty("stmt", QVariant::fromValue<void*>(pStmt));
    pValue->setText(pStmt->m_Value);
    pValue->setToolTip("The value to compare the criterion against");
    connect(pValue, &QLineEdit::textChanged, this, &SmartPlaylistDlg::onValueChanged);

    pContainer->layout()->addWidget(pValue);
  }
}

void SmartPlaylistDlg::FillCriteriums(QComboBox* pCombo)
{
  pCombo->clear();

  for (int i = 0; i < (int)SmartPlaylistQuery::Criterium::ENUM_COUNT; ++i)
  {
    pCombo->addItem(SmartPlaylistQuery::ToUiString((SmartPlaylistQuery::Criterium)i));
  }
}

void SmartPlaylistDlg::FillConditions(QComboBox* pCombo, SmartPlaylistQuery::Criterium crit)
{
  pCombo->clear();

  std::vector<SmartPlaylistQuery::Comparison> comps;
  SmartPlaylistQuery::GetAllowedComparisons(crit, comps);

  for (size_t i = 0; i < comps.size(); ++i)
  {
    pCombo->addItem(SmartPlaylistQuery::ToUiString(comps[i]), QVariant::fromValue<int>((int)comps[i]));
  }
}

void SmartPlaylistDlg::Apply()
{
  m_Query.m_SortOrder = (SmartPlaylistQuery::SortOrder)SortPlaylistCombo->currentIndex();
  m_Query.m_iSongLimit = SongLimitSpinbox->value();

  m_Playlist->SetModified();

  SmartPlaylistModification mod;
  mod.m_Query = m_Query;
  mod.m_Type = SmartPlaylistModification::Type::ChangeQuery;

  m_Playlist->m_Recorder.AddModification(mod, m_Playlist);

  m_Playlist->Refresh(PlaylistRefreshReason::PlaylistModified);
}

void SmartPlaylistDlg::onCriteriumChanged(int index)
{
  QComboBox* pCombo = qobject_cast<QComboBox*>(sender());

  QComboBox* pCompareBox = pCombo->parentWidget()->findChild<QComboBox*>("Compare");
  SmartPlaylistQuery::Statement* pStmt = (SmartPlaylistQuery::Statement*)pCombo->property("stmt").value<void*>();

  pStmt->m_Criterium = (SmartPlaylistQuery::Criterium)index;

  SmartPlaylistQuery::AdjustComparison(pStmt->m_Criterium, pStmt->m_Compare);

  FillConditions(pCompareBox, pStmt->m_Criterium);
  pCompareBox->setCurrentText(SmartPlaylistQuery::ToUiString(pStmt->m_Compare));
}

void SmartPlaylistDlg::onCompareChanged(int index)
{
  QComboBox* pCombo = qobject_cast<QComboBox*>(sender());
  SmartPlaylistQuery::Statement* pStmt = (SmartPlaylistQuery::Statement*)pCombo->property("stmt").value<void*>();

  pStmt->m_Compare = (SmartPlaylistQuery::Comparison)pCombo->currentData().toInt();
}

void SmartPlaylistDlg::onFulfilChanged(int index)
{
  QComboBox* pCombo = qobject_cast<QComboBox*>(sender());
  SmartPlaylistQuery::ConditionGroup* pGroup = (SmartPlaylistQuery::ConditionGroup*)pCombo->property("group").value<void*>();

  pGroup->m_Fulfil = (SmartPlaylistQuery::Fulfil)index;
}

void SmartPlaylistDlg::onValueChanged(const QString& text)
{
  QLineEdit* pCombo = qobject_cast<QLineEdit*>(sender());
  SmartPlaylistQuery::Statement* pStmt = (SmartPlaylistQuery::Statement*)pCombo->property("stmt").value<void*>();

  pStmt->m_Value = text;
}

void SmartPlaylistDlg::on_ButtonBox_clicked(QAbstractButton* button)
{
  if (button == ButtonBox->button(QDialogButtonBox::StandardButton::Save))
  {
    Apply();

    accept();
    return;
  }

  if (button == ButtonBox->button(QDialogButtonBox::StandardButton::Cancel))
  {
    reject();
    return;
  }

  if (button == ButtonBox->button(QDialogButtonBox::StandardButton::Apply))
  {
    Apply();
    return;
  }
}

void SmartPlaylistDlg::onAddStatementClicked(bool)
{
  QPushButton* pButton = qobject_cast<QPushButton*>(sender());
  SmartPlaylistQuery::ConditionGroup* pGroup = (SmartPlaylistQuery::ConditionGroup*)pButton->property("group").value<void*>();

  pGroup->m_Statements.push_back(SmartPlaylistQuery::Statement());
  SmartPlaylistQuery::Statement& stmt = *pGroup->m_Statements.rbegin();

  AddStatementUI(pGroup, &stmt, pButton->parentWidget()->parentWidget());
}

void SmartPlaylistDlg::onRemoveSmtClicked(bool)
{
  QPushButton* pButton = qobject_cast<QPushButton*>(sender());
  SmartPlaylistQuery::ConditionGroup* pGroup = (SmartPlaylistQuery::ConditionGroup*)pButton->property("group").value<void*>();
  SmartPlaylistQuery::Statement* pStmt = (SmartPlaylistQuery::Statement*)pButton->property("stmt").value<void*>();

  for (auto it = pGroup->m_Statements.begin(); it != pGroup->m_Statements.end(); ++it)
  {
    SmartPlaylistQuery::Statement& otherStmt = *it;

    if (&otherStmt == pStmt)
    {
      pGroup->m_Statements.erase(it);
      break;
    }
  }

  delete pButton->parentWidget();
}

void SmartPlaylistDlg::onAddGroupClicked(bool)
{
  QPushButton* pButton = qobject_cast<QPushButton*>(sender());
  SmartPlaylistQuery::ConditionGroup* pGroup = (SmartPlaylistQuery::ConditionGroup*)pButton->property("group").value<void*>();

  pGroup->m_SubGroups.push_back(SmartPlaylistQuery::ConditionGroup());

  SmartPlaylistQuery::ConditionGroup& newGroup = *pGroup->m_SubGroups.rbegin();

  AddGroupUI(pGroup, &newGroup, pButton->parentWidget()->parentWidget());
}

void SmartPlaylistDlg::onRemoveGroupClicked(bool)
{
  QPushButton* pButton = qobject_cast<QPushButton*>(sender());
  SmartPlaylistQuery::ConditionGroup* pGroup = (SmartPlaylistQuery::ConditionGroup*)pButton->property("group").value<void*>();
  SmartPlaylistQuery::ConditionGroup* pParentGroup = (SmartPlaylistQuery::ConditionGroup*)pButton->property("parentgroup").value<void*>();

  for (auto it = pParentGroup->m_SubGroups.begin(); it != pParentGroup->m_SubGroups.end(); ++it)
  {
    SmartPlaylistQuery::ConditionGroup& thisGroup = *it;

    if (&thisGroup == pGroup)
    {
      pParentGroup->m_SubGroups.erase(it);
      break;
    }
  }

  delete pButton->parentWidget()->parentWidget();
}
