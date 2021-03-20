package com.simplemobiletools.smsmessenger.extensions;

import android.provider.Telephony
import com.android.i18n.phonenumbers.Phonenumber
import com.simplemobiletools.commons.models.SimpleContact;
import com.simplemobiletools.smsmessenger.models.Message;
import com.simplemobiletools.smsmessenger.models.MessageAttachment;
import com.simplemobiletools.smsmessenger.models.ThreadItem

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

class GatewayMessage(
    val id: Long,
    val body: String,
    val type: Int,
    val participants: ArrayList<SimpleContact>,
    val date: Int,
    val read: Boolean,
    val threadId: Long,
    val isMMS: Boolean,
    val attachment: MessageAttachment?,
    var senderName: String,
    var phonenumber: String,
    val senderPhotoUri: String,
    var subscriptionId: Int,
    val from: String,
    val from_id: String,
    val to: String,
    val to_id: String) : ThreadItem() {

    fun isReceivedMessage() = type == Telephony.Sms.MESSAGE_TYPE_INBOX
}
