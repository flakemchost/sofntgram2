package com.sofnt.messenger.profile;

import org.telegram.tgnet.TLRPC;

/** A second username that exists only in SoFntGram (it is not in Telegram). Shown next to the real ones. */
public class SofntUsername extends TLRPC.TL_username {

    public SofntUsername(String name) {
        this.username = name;
        this.active = true;
        this.editable = false;
    }
}
