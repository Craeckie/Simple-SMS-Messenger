package com.simplemobiletools.smsmessenger.extensions;


import android.content.Context;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

import com.macasaet.fernet.Key;
import com.macasaet.fernet.StringValidator;
import com.macasaet.fernet.Token;
import com.simplemobiletools.commons.models.SimpleContact;
import com.simplemobiletools.smsmessenger.models.Conversation;
import com.simplemobiletools.smsmessenger.models.Message;
import com.simplemobiletools.smsmessenger.models.MessageAttachment;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;

import java.nio.charset.Charset;
import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GatewayUtils {
    public enum MessageStatus {
        SENT,
        RECEIVED,
        FORWARDED,
        READ,
        EDITED,
        DELETED,
    }

    private static Pattern namePattern = Pattern.compile("^(.+) \\(\\+?([0-9]+)\\)$");

    private static Pattern idPattern = Pattern.compile("^ID: ([0-9]+)$");
    private static Pattern datePattern = Pattern.compile("^Date: ([0-9]+)$");
    private static Pattern headerPattern = Pattern.compile("^([A-Za-z]+): (.*)$");

    private static class UTF16Validator implements StringValidator {
        public Charset getCharset() {
            return Charset.forName("UTF-16BE");
        }

        @Override
        public TemporalAmount getTimeToLive() {
            return Duration.ofSeconds(3600);
        }
    }
    private static class UTF8Validator implements StringValidator {
        public Charset getCharset() {
            return Charset.forName("UTF-8");
        }

        @Override
        public TemporalAmount getTimeToLive() {
            return Duration.ofSeconds(3600 * 24);
        }
    }

    private static Key key = null;
    private static final StringValidator utf8Validator = new UTF8Validator();
    private static final StringValidator utf16Validator = new UTF16Validator();
    public static String decryptBody(Context context, String body) {
        if (!body.startsWith("%8%") && !body.startsWith("%16%"))
            return body;
        boolean isUTF8 = true;
        try {
            if (key == null) {
                String keyString = PreferenceManager.getDefaultSharedPreferences(context).getString("edit_text_preference_sms_key", null);
                //TODO: add setting, change key
                keyString = "LGWizuiZ1aFOMsaKBDhI6dcUv8DB_lwHL-QdHkC8Qbk=";
                if (keyString == null)
                    return body;
                key = new Key(keyString);
            }
            if (body.startsWith("%8%")) {
                body = body.substring("%8%".length());
                isUTF8 = true;
            } else { // %16%
                body = body.substring("%16%".length());
                isUTF8 = false;
            }

            Token token = Token.fromString(body);
            if (isUTF8)
                body = token.validateAndDecrypt(key, utf8Validator);
            else
                body = token.validateAndDecrypt(key, utf16Validator);

        } catch (Exception e) {
            Log.w("GW-decrypt", e);
            Log.d("GW-decrypt", body);
            String debug = "Exception when decrypting: " + e + "\n";
            if (isUTF8)
                debug += "(is UTF8)\n";
            Toast.makeText(context, debug, Toast.LENGTH_LONG);
            //body = debug + body;
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            String debug = "Throwable when decrypting: " + throwable.getMessage() + "\n";
            if (isUTF8)
                debug += "(is UTF8)\n";
            Toast.makeText(context, debug, Toast.LENGTH_LONG);
        }
        return body;
    }

    public static GatewayMessage tryParseGatewayMessage(String body, long smsDate) {
        String[] lines = body.split("\n");
        String identifier = lines[0].trim();
        long ID = 0;
        String from = "";
        String from_id = null;
        String to = "";
        String to_id = null;
        String type = null;
        String phone = null;
        String messageBody = "";
        Date date = new java.util.Date(smsDate);
        Boolean isSent = true;
        MessageStatus status = MessageStatus.FORWARDED;
        Map<String, String> otherHeaders = new HashMap<>();
        boolean messageStarted = false;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (messageStarted) {
                if (!messageBody.isEmpty())
                    messageBody += "\n";
                messageBody += line;
            } else if (line.isEmpty()) {
                messageStarted = true;
                messageBody = "";
            } else if (line.startsWith("Type: ")) {
                type = line.substring("Type: ".length()).toLowerCase();
            } else if (line.startsWith("From: ")) {
                from = line.substring("From: ".length());
                isSent = false;
            } else if (line.startsWith("From_id: ")) {
                from_id = line.substring("From_id: ".length());
                isSent = false;
            } else if (line.startsWith("To: ")) {
                to = line.substring("To: ".length());
            } else if (line.startsWith("To_id: ")) {
                to_id = line.substring("To_id: ".length());
            } else if (line.startsWith("Group: ")) {
                to = line.substring("Group: ".length());
                type = "group";
            } else if (line.startsWith("Channel: ")) {
                to = line.substring("Channel: ".length());
                type = "channel";
            } else if (line.startsWith("Phone: ")) {
                phone = line.substring("Phone: ".length());
            } else if (line.startsWith("Edit: ")) {
//                if (line.substring("Edit: ".length()).equalsIgnoreCase("true")) {
//                    status = MessageStatus.EDITED;
//                }
            } else if (line.startsWith("Status: ")) {
                String statusString = line.substring("Status: ".length());
                status = MessageStatus.valueOf(statusString.toUpperCase());
//                if (status == MessageStatus.FORWARDED) {
//                    // Ignore the messages just indicating that this message was about to be sent to TG
//                    return new UserMessage(Long.MIN_VALUE, new Date(0), "", "", null, false, MessageStatus.FORWARDED);
//                }
            } else if (line.startsWith("Date: ")) {
                Matcher dateMatch = datePattern.matcher(line);
                if (dateMatch.matches())
                    date = new java.util.Date(Long.parseLong(dateMatch.group(1)) * 1000L);
            } else if (line.startsWith("Buttons: ")) {
//                String buttonsStr = line.substring("Buttons: ".length());
//                buttons = new Buttons();
//                try {
//                    JSONArray arr = new JSONArray(buttonsStr);
//                    for (int j = 0; j < arr.length(); j++) {
//                        JSONArray data_row = arr.getJSONArray(j);
//                        ArrayList<String> items = new ArrayList<>();
//                        for (int k = 0; k < data_row.length(); k++) {
//                            items.add(data_row.getString(k));
//                        }
//                        buttons.addRow(items);
//                    }
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                }
//                    Log.v("GU", buttons.toString());

            } else if (idPattern.matcher(line).matches()) { // For performance the last check
                try {
                    ID = Integer.parseInt(line.substring("ID: ".length()));
                } catch (NumberFormatException e) {
//                    Toast.makeText(getApplicationContext(), "Invalid " + line, Toast.LENGTH_LONG);
                }
            } else {
                Matcher matcher = headerPattern.matcher(line);
                if (matcher.matches()) {
                    otherHeaders.put(matcher.group(1), matcher.group(2));
                }
            }
//                } else { // Unknown header, assume that message starts for now..
//                    messageStarted = true;
//                    messageBody = line;
//                }
        }
        Conversation conversation = null;
        GatewayMessage msg = null;
        if (!identifier.equals("TG") && to_id == null) {
            Log.w("GW", "to_id is null!");
        }
        if (!messageBody.isEmpty() && !messageBody.equals("\n") && identifier.equals("MX")) {
//            conversation = new Conversation(
//                isSent ? to.hashCode() : from.hashCode(),
//                messageBody,
//                (int) date.getTime(),
//                status == MessageStatus.READ,
//                to,
//                "",
//                type.equals("Group") || type.equals("Channel"),
//                phone == null ? to : phone
//            );
            ArrayList participants = new ArrayList();
            if (from != null)
                participants.add(from);
             msg = new GatewayMessage(
                ID,
                messageBody,
                isSent ? Telephony.Sms.MESSAGE_TYPE_SENT : Telephony.Sms.MESSAGE_TYPE_INBOX,
                participants,
                 date.getTime(),
                status == MessageStatus.READ,
                to_id == null ? from_id.hashCode() : to_id.hashCode(),
                false,
                null,
                isSent ? "Me" : from,
                phone == null ? (isSent ? "Me" : from_id == null ? from : from_id) : phone,
                "",
                0,
                 from,
                 from_id,
                 to,
                 to_id
            );
        }
        return msg;
    }

//    public static BaseMessage tryParseGatewayMessage(Context context, String body, Date receivedDate, boolean isSMSSent, String phoneNumber) {
//
//        body = decryptBody(context, body);
//        BaseMessage message = null;
//        int ID = -1;
//        MessageStatus status = MessageStatus.FORWARDED;
//
//        String[] lines = body.split("\n");
//        String identifier = null;
//        String from = null;
//        String from_id = null;
//        String to = null;
//        String to_id = null;
//        if (lines.length > 2) {
//            identifier = lines[0].trim();
//            String type = null;
////            String group = null;
////            String channel = null;
//            String phone = null;
//            String messageBody = "";
//            Buttons buttons = null;
////            boolean isEdit = false;
//            Date date = receivedDate;
//            Boolean isSent = true;
//            Map<String, String> otherHeaders = new HashMap<>();
//
//            ChatList chatList = Messengers.listForIdentifier(context, identifier);
//            if (chatList == null) {
//                chatList = Messengers.getSMS(context);
//                identifier = "SMS";
//            } else if (identifier.equalsIgnoreCase("EM")) {
//                type = "group";
//            }
//            boolean messageStarted = false;
//            for (int i = 1; i < lines.length; i++) {
//                String line = lines[i];
//                if (messageStarted) {
//                    if (!messageBody.isEmpty())
//                        messageBody += "\n";
//                    messageBody += line;
//                } else if (line.isEmpty()) {
//                    messageStarted = true;
//                    messageBody = "";
//                } else if (line.startsWith("Type: ")) {
//                    type = line.substring("Type: ".length()).toLowerCase();
//                } else if (line.startsWith("From: ")) {
//                    from = line.substring("From: ".length());
//                    isSent = false;
//                    // TODO: temporarily support old format for messages sent to groups
//                    if (phone == null && type == null) {
//                        Matcher matcherGroup = oldFromGroupPattern.matcher(line);
//                        Matcher matcherNumber = oldNumberPattern.matcher(line);
//                        if (matcherGroup.matches()) {
//                            from = matcherGroup.group(1);
//                            phone = matcherGroup.group(2);
//                            to = matcherGroup.group(3);
//                            type = "group";
//                        } else if (matcherNumber.matches()) {
//                            from = matcherNumber.group(2);
//                            phone = matcherNumber.group(3);
//                            type = "user";
//                        }
//                    }
//                } else if (line.startsWith("From_id: ")) {
//                    from_id = line.substring("From_id: ".length());
//                    isSent = false;
//                } else if (line.startsWith("To: ")) {
//                    to = line.substring("To: ".length());
//                } else if (line.startsWith("To_id: ")) {
//                    to_id = line.substring("To_id: ".length());
//                } else if (line.startsWith("Group: ")) {
//                    to = line.substring("Group: ".length());
//                    type = "group";
//                } else if (line.startsWith("Channel: ")) {
//                    to = line.substring("Channel: ".length());
//                    type = "channel";
//                } else if (line.startsWith("Phone: ")) {
//                    phone = line.substring("Phone: ".length());
//                } else if (line.startsWith("Edit: ")) {
//                    if (line.substring("Edit: ".length()).equalsIgnoreCase("true")) {
//                        status = MessageStatus.EDITED;
//                    }
//                } else if (line.startsWith("Status: ")) {
//                    String statusString = line.substring("Status: ".length());
//                    status = MessageStatus.valueOf(statusString.toUpperCase());
//                    if (status == MessageStatus.FORWARDED) {
//                        // Ignore the messages just indicating that this message was about to be sent to TG
//                        return new UserMessage(Long.MIN_VALUE, new Date(0), "", "", null, false, MessageStatus.FORWARDED);
//                    }
//                } else if (line.startsWith("Date: ")) {
//                    Matcher dateMatch = datePattern.matcher(line);
//                    if (dateMatch.matches())
//                        date = new java.util.Date(Long.parseLong(dateMatch.group(1)) * 1000L);
//                } else if (line.startsWith("Buttons: ")) {
//                    String buttonsStr = line.substring("Buttons: ".length());
//                    buttons = new Buttons();
//                    try {
//                        JSONArray arr = new JSONArray(buttonsStr);
//                        for (int j = 0; j < arr.length(); j++) {
//                            JSONArray data_row = arr.getJSONArray(j);
//                            ArrayList<String> items = new ArrayList<>();
//                            for (int k = 0; k < data_row.length(); k++) {
//                                items.add(data_row.getString(k));
//                            }
//                            buttons.addRow(items);
//                        }
//                    } catch (JSONException e) {
//                        e.printStackTrace();
//                    }
////                    Log.v("GU", buttons.toString());
//
//                } else if (idPattern.matcher(line).matches()) { // For performance the last check
//                    try {
//                        ID = Integer.parseInt(line.substring("ID: ".length()));
//                    } catch (NumberFormatException e) {
//                        Toast.makeText(context, "Invalid " + line, Toast.LENGTH_LONG);
//                    }
//                } else {
//                    Matcher matcher = headerPattern.matcher(line);
//                    if (matcher.matches()) {
//                        otherHeaders.put(matcher.group(1), matcher.group(2));
//                    }
//                }
////                } else { // Unknown header, assume that message starts for now..
////                    messageStarted = true;
////                    messageBody = line;
////                }
//            }
//
//            if (!messageBody.isEmpty() && !messageBody.equals("\n")) {
//                if (from != null || to != null) {
//                    String from_identifier = from_id != null ? from_id : (phone != null ? phone : from);
//                    if (type != null && !type.equals("user")) {
//                        if (type.equals("group") || type.equals("channel")) {
//                            if (isSent && to != null) { // I wrote to a group
//                                message = new GroupMessage(
//                                    ID,
//                                    date,
//                                    messageBody,
//                                    identifier,
//                                    chatList.get_or_create_group(context, to, to_id, type.equals("channel")),
//                                    type.equals("channel") ? null : chatList.get_meUser(context), // TODO: correct?
//                                    type.equals("channel") ? false : isSent,
//                                    status,
//                                    otherHeaders);
//                            } else if (!isSent && to != null && from != null) { // Somebody sent to a group
//                                message = new GroupMessage(
//                                    ID,
//                                    date,
//                                    messageBody,
//                                    identifier,
//                                    chatList.get_or_create_group(context, to, to_id, false),
//                                    chatList.get_or_create_user(context, from, from_identifier, phone),
//                                    isSent,
//                                    status,
//                                    otherHeaders);
//                            } else if (!isSent && from == null) { // Likely a channel
//                                message = new GroupMessage(
//                                    ID,
//                                    date,
//                                    messageBody,
//                                    identifier,
//                                    chatList.get_or_create_group(context, to, to_id, true),
//                                    null,
//                                    isSent,
//                                    status,
//                                    otherHeaders);
//                            } else {
//                                Toast.makeText(context, "Invalid group message:\n" + body, Toast.LENGTH_LONG);
//                            }
//                        } else {
//                            Toast.makeText(context, "Unknown type: " + type, Toast.LENGTH_LONG);
//                        }
//                    } else { // normal user message
//                        if (isSent && to != null) { // I wrote to a user
//                            message = new UserMessage(
//                                ID,
//                                date,
//                                messageBody,
//                                identifier,
//                                chatList.get_or_create_user(context, to, to_id, phone),
//                                isSent,
//                                status,
//                                otherHeaders);
//                        } else if (!isSent && from != null) { // Somebody sent me a message
//                            message = new UserMessage(
//                                ID,
//                                date,
//                                messageBody,
//                                identifier,
//                                chatList.get_or_create_user(context, from, from_identifier, phone),
//                                isSent,
//                                status,
//                                otherHeaders);
//                        } else {
//                            Toast.makeText(context, "Invalid message:\n" + body, Toast.LENGTH_LONG);
//                        }
//                    }
//                    if (message != null && buttons != null) {
//                        message.setButtons(buttons);
//                    }
//                } else if (type != null && type.equals("Info")) {
//                    message = new UserMessage(
//                        ID,
//                        receivedDate,
//                        body,
//                        identifier,
//                        Messengers.getSMS(context).get_or_create_user(context, phoneNumber, phoneNumber, phoneNumber),
//                        isSMSSent,
//                        status
//                    );
//                }
//            }
//        }
//        if (message == null) {
//            //TODO: only temporary
//            if (status == MessageStatus.READ)
//                if (to == null && from == null)
//                    return null;
//                else
//                    Log.d("GU", message.getMessage());
//            if (identifier == null)
//                identifier = "SMS";
//            message = new UserMessage(
//                ID,
//                receivedDate,
//                body,
//                identifier,
//                Messengers.getSMS(context).get_or_create_user(context, phoneNumber, phoneNumber, phoneNumber),
//                isSMSSent,
//                status
//            );
//        }
//        return message;
//    }

}
