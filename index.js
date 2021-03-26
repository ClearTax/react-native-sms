//@flow
'use strict';

import { NativeModules, PermissionsAndroid, Platform } from 'react-native'

async function checkAndroidReadSmsAuthorized() {
  if (Platform.OS !== 'android') {
    return false;
  }

  let authorized;

  try {
    authorized = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.READ_SMS);
  } catch (error) {
    // do nothing
  }

  return authorized === PermissionsAndroid.RESULTS.GRANTED;
}

function isAuthorizedForCallback(androidCanReadSms) {
  return Platform.OS !== 'android' || androidCanReadSms;
}

async function send(options: Object, callback: () => void, timeout: number) {
  const androidCanReadSms = await checkAndroidReadSmsAuthorized();

  options.isAuthorizedForCallback = isAuthorizedForCallback(androidCanReadSms);

  if (options.isAuthorizedForCallback || options.allowAndroidSendWithoutReadPermission) {
    NativeModules.SendSMS.send(options, callback, timeout);
  }
}

const SendSMS = {
  send
}

module.exports = SendSMS;
