import { NativeModules, DeviceEventEmitter } from "react-native";
import NotificationAndroid from "./notification";

const RNNotifications = NativeModules.WixRNNotifications;

let notificationReceivedListener;
let notificationReceivedInForegroundListener;
let notificationOpenedListener;
let registrationTokenUpdateListener;
let notificationActionPressedListener;

export class NotificationsAndroid {
  static setNotificationOpenedListener(listener) {
    notificationOpenedListener = DeviceEventEmitter.addListener(
      "notificationOpened",
      notification => listener(new NotificationAndroid(notification))
    );
  }

  static clearNotificationOpenedListener() {
    if (notificationOpenedListener) {
      notificationOpenedListener.remove();
      notificationOpenedListener = null;
    }
  }

  static setNotificationReceivedListener(listener) {
    notificationReceivedListener = DeviceEventEmitter.addListener(
      "notificationReceived",
      notification => listener(new NotificationAndroid(notification))
    );
  }

  static setNotificationReceivedInForegroundListener(listener) {
    notificationReceivedInForegroundListener = DeviceEventEmitter.addListener(
      "notificationReceivedInForeground",
      notification => listener(new NotificationAndroid(notification))
    );
  }

  static clearNotificationReceivedListener() {
    if (notificationReceivedListener) {
      notificationReceivedListener.remove();
      notificationReceivedListener = null;
    }
  }

  static clearNotificationReceivedInForegroundListener() {
    if (notificationReceivedInForegroundListener) {
      notificationReceivedInForegroundListener.remove();
      notificationReceivedInForegroundListener = null;
    }
  }

  static setRegistrationTokenUpdateListener(listener) {
    registrationTokenUpdateListener = DeviceEventEmitter.addListener(
      "remoteNotificationsRegistered",
      listener
    );
  }

  static clearRegistrationTokenUpdateListener() {
    if (registrationTokenUpdateListener) {
      registrationTokenUpdateListener.remove();
      registrationTokenUpdateListener = null;
    }
  }

  static setNotificationActionPressedListener(listener) {
    notificationActionPressedListener = DeviceEventEmitter.addListener(
      "notificationActionPressed",
      listener
    );
  }

  static clearNotificationActionPressedListener() {
    if (notificationActionPressedListener) {
      notificationActionPressedListener.remove();
      notificationActionPressedListener = null;
    }
  }

  static async isRegisteredForRemoteNotifications() {
    return await RNNotifications.isRegisteredForRemoteNotifications();
  }

  static refreshToken() {
    RNNotifications.refreshToken();
  }

  static localNotification(notification: Object) {
    const id = (Math.random() * 100000000) | 0; // Bitwise-OR forces value onto a 32bit limit
    RNNotifications.postLocalNotification(notification, id);
    return id;
  }

  static cancelLocalNotification(id) {
    RNNotifications.cancelLocalNotification(id);
  }
}

export class PendingNotifications {
  static getInitialNotification() {
    return RNNotifications.getInitialNotification().then(rawNotification => {
      return rawNotification
        ? new NotificationAndroid(rawNotification)
        : undefined;
    });
  }
}
