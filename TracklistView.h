#pragma once

#include <QTreeView>
#include <QItemDelegate>

class TrackListView : public QTreeView
{
  Q_OBJECT

public:
  TrackListView(QWidget* parent);

signals:
  void DeleteItems();

protected:
  virtual void keyPressEvent(QKeyEvent* e) override;

  virtual void startDrag(Qt::DropActions supportedActions) override;

};

class RatingItemDelegate : public QItemDelegate
{
  Q_OBJECT

public:
  RatingItemDelegate(QObject *parent);

  virtual QSize sizeHint(const QStyleOptionViewItem &option, const QModelIndex &index) const override;

protected:
  virtual void paint(QPainter *painter, const QStyleOptionViewItem &option, const QModelIndex &index) const override;
  virtual bool editorEvent(QEvent* event, QAbstractItemModel* model, const QStyleOptionViewItem& option, const QModelIndex& index) override;

  QPixmap m_Pixmap, m_PixmapGrey;
};
