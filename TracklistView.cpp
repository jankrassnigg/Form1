#include "TrackListView.h"
#include "MusicLibrary.h"
#include <QDrag>
#include <QKeyEvent>
#include <QMouseEvent>
#include <QPainter>

TrackListView::TrackListView(QWidget* parent)
    : QTreeView(parent)
{
  setDragEnabled(true);

  setItemDelegateForColumn(0, new RatingItemDelegate(this));
  setEditTriggers(QAbstractItemView::EditTrigger::NoEditTriggers);

  setSortingEnabled(true);
}

void TrackListView::keyPressEvent(QKeyEvent* e)
{
  if (e->key() == Qt::Key_Delete)
  {
    emit DeleteItems();
  }

  if (e->key() == Qt::Key_Return || e->key() == Qt::Key_Enter)
  {
    emit StartCurrentItem();
  }

  QTreeView::keyPressEvent(e);
}

void TrackListView::startDrag(Qt::DropActions supportedActions)
{
  //Q_D(QAbstractItemView);
  QModelIndexList indexes = selectedIndexes();
  if (indexes.count() > 0)
  {
    QMimeData* data = model()->mimeData(indexes);
    if (!data)
      return;
    QRect rect;
    //QPixmap pixmap = d->renderToPixmap(indexes, &rect);
    rect.adjust(horizontalOffset(), verticalOffset(), 0, 0);
    QDrag* drag = new QDrag(this);
    //drag->setPixmap(pixmap);
    drag->setMimeData(data);
    //drag->setHotSpot(d->pressedPosition - rect.topLeft());
    //drag->setHotSpot(QPoint(pixmap.width() / 2, pixmap.height() / 2));
    Qt::DropAction defaultDropAction = Qt::CopyAction;
    //if (d->defaultDropAction != Qt::IgnoreAction && (supportedActions & d->defaultDropAction))
    //defaultDropAction = d->defaultDropAction;
    //else if (supportedActions & Qt::CopyAction && dragDropMode() != QAbstractItemView::InternalMove)
    //defaultDropAction = Qt::CopyAction;

    if (drag->exec(supportedActions, defaultDropAction) == Qt::MoveAction)
    {
      //d->clearOrRemove();
    }

    // Reset the drop indicator
    //d->dropIndicatorRect = QRect();
    //d->dropIndicatorPosition = OnItem;
  }
}

RatingItemDelegate::RatingItemDelegate(QObject* parent)
    : QItemDelegate(parent)
{
  m_Pixmap = QIcon(":/icons/icons/star.png").pixmap(16);
  m_PixmapGrey = QIcon(":/icons/icons/star_grey.png").pixmap(16);
}

void RatingItemDelegate::paint(QPainter* painter, const QStyleOptionViewItem& option, const QModelIndex& index) const
{
  if (option.state & QStyle::State_Selected)
    painter->fillRect(option.rect, option.palette.highlight());
  else
    painter->fillRect(option.rect, option.palette.alternateBase());

  const int rating = qvariant_cast<int>(index.data(Qt::DisplayRole));

  QRect r = option.rect;
  r.setHeight(16);
  r.setLeft(20);
  r.setRight(20 + 5 * 16);

  painter->setClipRect(r);

  if (option.state & QStyle::State_Selected)
    painter->fillRect(r, option.palette.highlight());
  else
    painter->fillRect(r, option.palette.alternateBase());

  for (int i = 0; i < rating; ++i)
  {
    r.setWidth(16);
    painter->drawPixmap(r, m_Pixmap);
    r.setLeft(r.left() + 16);
  }

  for (int i = rating; i < 5; ++i)
  {
    r.setWidth(16);
    painter->drawPixmap(r, m_PixmapGrey);
    r.setLeft(r.left() + 16);
  }
}

bool RatingItemDelegate::editorEvent(QEvent* event, QAbstractItemModel* model, const QStyleOptionViewItem& option, const QModelIndex& index)
{
  if (event->type() == QEvent::Type::MouseButtonPress)
  {
    QMouseEvent* mouseEvent = (QMouseEvent*)event;

    int x = mouseEvent->pos().x();
    int newRating = (x - 20) / 16 + 1;

    if (newRating > 5)
      return false;

    if (x < 20)
      newRating = 0;

    QString guid = index.data(Qt::UserRole + 1).toString();

    MusicLibrary::GetSingleton()->UpdateSongRating(guid, newRating, true);

    model->dataChanged(index, index);
  }

  return false;
}

QSize RatingItemDelegate::sizeHint(const QStyleOptionViewItem& option, const QModelIndex& index) const
{
  return QSize(16 * 5 + 20, 16);
}
