/** An in-app notification (NFR-016). */
export interface AppNotification {
  id: number;
  type: string;
  title: string;
  body: string;
  /** The page in the app the notification opens, or null. */
  link?: string | null;
  createdAt: string;
  read: boolean;
}

/** The bell: the unread count and the 30 newest notifications. */
export interface NotificationList {
  unread: number;
  items: AppNotification[];
}
