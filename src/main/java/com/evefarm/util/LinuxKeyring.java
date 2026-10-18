package com.evefarm.util;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

final class LinuxKeyring implements KeyringProtection.KeyStore {

    static final String KEY_NAME = "token-key";
    private static final Logger LOG = Logger.getLogger(LinuxKeyring.class.getName());
    private static final String SCHEMA_NAME = "com.evefarm.Secret";
    private static final String APPLICATION = "com.evefarm";
    private static final String LABEL = "EVE Farm login key";
    private static final int SCHEMA_NONE = 0;
    private static final int ATTRIBUTE_STRING = 0;
    private static final int SEARCH_ALL = 1 << 1;
    private static final int GERROR_MESSAGE_OFFSET = 8;

    interface LibSecret extends Library {
        Pointer secret_schema_new(String name, int flags, Object... attributes);

        void secret_schema_unref(Pointer schema);

        boolean secret_password_store_sync(Pointer schema, String collection, String label, String password,
                                           Pointer cancellable, PointerByReference error, Object... attributes);

        Pointer secret_password_lookup_sync(Pointer schema, Pointer cancellable, PointerByReference error,
                                            Object... attributes);

        Pointer secret_password_search_sync(Pointer schema, int flags, Pointer cancellable,
                                            PointerByReference error, Object... attributes);

        void secret_password_free(Pointer password);
    }

    interface GLib extends Library {
        void g_error_free(Pointer error);

        void g_list_free_full(Pointer list, Pointer freeFunction);
    }

    private record Natives(LibSecret secret, GLib glib, Pointer objectUnref) {
    }

    private final String keyName;
    private Natives natives;
    private boolean loaded;

    LinuxKeyring() {
        this(KEY_NAME);
    }

    LinuxKeyring(String keyName) {
        this.keyName = keyName;
    }

    @Override
    public synchronized boolean isAvailable() {
        return natives() != null;
    }

    @Override
    public synchronized Optional<String> load() {
        Natives library = required();
        Pointer schema = schema(library);
        try {
            PointerByReference error = new PointerByReference();
            Pointer password = library.secret().secret_password_lookup_sync(schema, null, error,
                    "application", APPLICATION, "key", keyName, null);
            throwIfFailed(library, error, "read");
            if (password == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(password.getString(0, "UTF-8"));
            } finally {
                library.secret().secret_password_free(password);
            }
        } finally {
            library.secret().secret_schema_unref(schema);
        }
    }

    @Override
    public synchronized boolean exists() {
        Natives library = required();
        Pointer schema = schema(library);
        try {
            PointerByReference error = new PointerByReference();
            Pointer items = library.secret().secret_password_search_sync(schema, SEARCH_ALL, null, error,
                    "application", APPLICATION, "key", keyName, null);
            throwIfFailed(library, error, "check");
            if (items == null) {
                return false;
            }
            library.glib().g_list_free_full(items, library.objectUnref());
            return true;
        } catch (UnsatisfiedLinkError e) {
            LOG.log(Level.INFO, "This libsecret can't list locked keyring items", e);
            return false;
        } finally {
            library.secret().secret_schema_unref(schema);
        }
    }

    @Override
    public synchronized void save(String key) {
        Natives library = required();
        Pointer schema = schema(library);
        try {
            PointerByReference error = new PointerByReference();
            boolean stored = library.secret().secret_password_store_sync(schema, null, LABEL, key, null, error,
                    "application", APPLICATION, "key", keyName, null);
            throwIfFailed(library, error, "save");
            if (!stored) {
                throw new IllegalStateException("Your desktop keyring didn't save EVE Farm's key.");
            }
        } finally {
            library.secret().secret_schema_unref(schema);
        }
    }

    private Natives natives() {
        if (!loaded) {
            loaded = true;
            natives = loadNatives();
        }
        return natives;
    }

    private Natives required() {
        Natives library = natives();
        if (library == null) {
            throw new IllegalStateException("libsecret isn't installed, so EVE Farm can't use your desktop keyring.");
        }
        return library;
    }

    private static Natives loadNatives() {
        try {
            LibSecret secret = Native.load("libsecret-1.so.0", LibSecret.class);
            GLib glib = Native.load("libglib-2.0.so.0", GLib.class);
            Pointer objectUnref = NativeLibrary.getInstance("libgobject-2.0.so.0").getFunction("g_object_unref");
            return new Natives(secret, glib, objectUnref);
        } catch (LinkageError | RuntimeException e) {
            LOG.log(Level.WARNING, "libsecret couldn't be loaded, so the desktop keyring can't be used", e);
            return null;
        }
    }

    private static Pointer schema(Natives library) {
        Pointer schema = library.secret().secret_schema_new(SCHEMA_NAME, SCHEMA_NONE,
                "application", ATTRIBUTE_STRING, "key", ATTRIBUTE_STRING, null);
        if (schema == null) {
            throw new IllegalStateException("libsecret couldn't describe EVE Farm's keyring item.");
        }
        return schema;
    }

    private static void throwIfFailed(Natives library, PointerByReference error, String action) {
        Pointer failure = error.getValue();
        if (failure == null) {
            return;
        }
        String message;
        try {
            Pointer text = failure.getPointer(GERROR_MESSAGE_OFFSET);
            message = text == null ? "no details" : text.getString(0, "UTF-8");
        } finally {
            library.glib().g_error_free(failure);
        }
        throw new IllegalStateException("EVE Farm couldn't " + action + " its key in your desktop keyring ("
                + message + "). Check that GNOME Keyring, KWallet or another Secret Service keyring is "
                + "running and unlocked.");
    }
}
