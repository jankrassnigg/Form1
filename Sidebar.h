#pragma once

#include "Common.h"
#include <QAbstractItemModel>

class Sidebar : public QAbstractItemModel
{
  Q_OBJECT

public:
  explicit Sidebar(QObject* parent);

  virtual QModelIndex index(int row, int column, const QModelIndex& parent = QModelIndex()) const override;
  virtual QModelIndex parent(const QModelIndex& child) const override;
  virtual int rowCount(const QModelIndex& parent = QModelIndex()) const override;
  virtual int columnCount(const QModelIndex& parent = QModelIndex()) const override;
  virtual QVariant data(const QModelIndex& index, int role = Qt::DisplayRole) const override;

  virtual bool canDropMimeData(const QMimeData *data, Qt::DropAction action, int row, int column, const QModelIndex &parent) const override;
  virtual bool dropMimeData(const QMimeData *data, Qt::DropAction action, int row, int column, const QModelIndex &parent) override;
  virtual Qt::ItemFlags flags(const QModelIndex &index) const override;

private slots:
  void onPlaylistsChanged();
};
