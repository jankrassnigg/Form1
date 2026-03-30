#pragma once

#include "Misc/Common.h"
#include "Playlists/Smart/SmartPlaylistQuery.h"
#include <QDialog>
#include <ui_SmartPlaylistDlg.h>

class QComboBox;
class SmartPlaylist;

class SmartPlaylistDlg : public QDialog, Ui_SmartPlaylistDlg
{
  Q_OBJECT

public:
  SmartPlaylistDlg(SmartPlaylist* playlist, QWidget* parent);

  const SmartPlaylistQuery& GetQuery() const { return m_Query; }

private slots:
  void onCriteriumChanged(int index);
  void onCompareChanged(int index);
  void onFulfilChanged(int index);
  void onValueChanged(const QString& text);
  void onAddStatementClicked(bool);
  void onRemoveSmtClicked(bool);
  void onAddGroupClicked(bool);
  void onRemoveGroupClicked(bool);
  void on_ButtonBox_clicked(QAbstractButton* button);

private:
  void RebuildUI();
  QWidget* AddGroupUI(SmartPlaylistQuery::ConditionGroup* pParentGroup, SmartPlaylistQuery::ConditionGroup* pGroup, QWidget* pParentWidget);
  void AddStatementUI(SmartPlaylistQuery::ConditionGroup* pGroup, SmartPlaylistQuery::Statement* pStmt, QWidget* pParentWidget);
  void FillCriteriums(QComboBox* pCombo);
  void FillConditions(QComboBox* pCombo, SmartPlaylistQuery::Criterium crit);
  void Apply();

  SmartPlaylist* m_Playlist = nullptr;
  SmartPlaylistQuery m_Query;
};
