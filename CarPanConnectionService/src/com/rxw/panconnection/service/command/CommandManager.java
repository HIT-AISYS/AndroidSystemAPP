package com.rxw.panconnection.service.command;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;

import com.rxw.panconnection.control.PanConnectionManager;
import com.rxw.panconnection.control.command.Command;
import com.rxw.panconnection.control.command.ICommandCallback;
import com.rxw.panconnection.service.util.Logger;

public class CommandManager {

    private static final String TAG = "CommandManager";

    private final static String PKG_SUBSCRIBE_KEY_COMMAND = "subscribeCommands";
    private final static String PKG_SUBSCRIBE_KEY_RECEIVER_ACTION = "subscribeCommandsReceiverAction";

    private final static String[] SUBSCRIBED_PACKAGES = new String[]{
            "com.rxw.car.panconnection"
    };

    private static CommandManager sInstance;
    private PanConnectionManager mPanConnectionManager;

    private Context mContext;

    public static CommandManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (CommandManager.class) {
                if (sInstance == null) {
                    sInstance = new CommandManager(context);
                }
            }
        }
        return sInstance;
    }

    private CommandManager(Context context) {
        mContext = context;
        mPanConnectionManager = PanConnectionManager.getInstance(context);
    }

    public void subscribeDDSCommand() {
        for (String pkg : SUBSCRIBED_PACKAGES) {
            try {
                Bundle bundle = mContext.getPackageManager().getApplicationInfo(pkg, PackageManager.GET_META_DATA)
                        .metaData;
                String subscribeCommands = bundle.getString("subscribeCommands");
                Logger.d(TAG, "subscribeCommands: " + subscribeCommands);
                if (!TextUtils.isEmpty(subscribeCommands)) {
                    String subscribeCommandsAction = bundle.getString("subscribeCommandsReceiverAction");
                    if (subscribeCommands.contains(",")) {
                        String[] commands = subscribeCommands.split(",");
                        for (String command : commands) {
                            subscribeSingleCommand(pkg, subscribeCommandsAction, command);
                        }
                    } else {
                        subscribeSingleCommand(pkg, subscribeCommandsAction, subscribeCommands);
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                Logger.e(TAG, "subscribeDDSCommand not found pkg: " + pkg);
            }
        }
    }

    private void subscribeSingleCommand(String pkg, String subscribeCommandsAction, String commandStr) {
        mPanConnectionManager.subscribeCommand(Command.create(commandStr), new ICommandCallback() {
            @Override
            public void onCommandReceived(Command command) {
                Logger.i(TAG, "onCommandReceived: " + command);
                Intent intent = new Intent(subscribeCommandsAction);
                intent.setPackage(pkg);
                intent.putExtra("command", command.toString());
                mContext.sendBroadcast(intent);
            }
        });
    }

    public void sendCommand(Command command) {
        mPanConnectionManager.sendCommand(command);
    }
}
