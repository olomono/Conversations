package eu.siacs.conversations.crypto.axolotl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.XmppUri;
import rocks.xmpp.addr.Jid;

/**
 * This is the core of the implementation of XEP-XXXX: Automatic Trust Transfer.
 */
public class AutomaticTrustTransfer {
    /**
     * XMPP URI action for trust messages.
     * See XEP-XXXX: Automatic Trust Transfer under "trust message".
     */
    public static final String ACTION_TRUST = "omemo-trust";

    // TODO Delete after testing.
    /**
     * XMPP URI key for simulated manual authentication.
     * This is used for testing purposes.
     */
    public static final String ACTION_KEY_INITIATE = "init";

    /**
     * XMPP URI key for authentication.
     * See XEP-XXXX: Automatic Trust Transfer under "authentication message".
     */
    public static final String ACTION_KEY_AUTHENTICATE = "auth";

    /**
     * XMPP URI key for revocation.
     * See XEP-XXXX: Automatic Trust Transfer under "revocation message".
     */
    public static final String ACTION_KEY_REVOKE = "revoke";

    /**
     * Table name for the key fingerprints of senders of a trust message which could not be used for authentication directly since those keys were not already authenticated.
     */
    public static final String TABLE_ATT_CACHE = "att_cache";

    /**
     * Column name of TABLE_ATT_CACHE for authentication (true) or revocation (false).
     */
    public static final String COLUMN_SENDER_FINGERPRINT = "sender_fingerprint";

    /**
     * Column name of TABLE_ATT_CACHE for authentication (1) or revocation (0).
     */
    public static final String COLUMN_TRUST = "trust";

    /**
     * Authenticates a key or revokes the trust in a key if the given message contains a URI for trust messages.
     * 
     * @param xmppConnectionService Service of the XMPP connection.
     * @param message Message which may be a trust message.
     * @return true if the given message is a valid trust message, false otherwise.
     */
    public static boolean authenticateOrRevoke(XmppConnectionService xmppConnectionService, Message message) {
        if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
            XmppUri uri = new XmppUri(message.getBody().trim());
            if (uri.isAction(ACTION_TRUST) && uri.hasFingerprintsForAuthenticationOrRevocation()) {
                Contact sender = message.getContact();
                Contact keysOwner = message.getContact().getAccount().getRoster().getContact(Jid.of(uri.getJid()));

                // A trust messages from an own device may authenticate or revoke the keys of all own devices and devices of contacts.
                // Whereas trust messages from devices of contacts may only authenticate or revoke the keys of their own devices.
                if ((sender.isSelf() || keysOwner.equals(sender))) {
                    String infoMessageBody = null;

                    // TODO Delete after testing.
                    if (authenticateInit(xmppConnectionService, keysOwner, uri.getFingerprintsForInitialization())) {
                        infoMessageBody = "Init - This should only be shown for testing.";
                    }

                    if (keysOwner.getAccount().getAxolotlService().getFingerprintTrust(message.getFingerprint()).isVerified()) {
                        if (authenticate(xmppConnectionService, keysOwner, uri.getFingerprintsForAuthentication())) {
                            infoMessageBody = createInfoMessageBody(keysOwner, infoMessageBody, true, true);
                        }
                        if (revoke(keysOwner, uri.getFingerprintsForRevocation())) {
                            infoMessageBody = createInfoMessageBody(keysOwner, infoMessageBody, false, true);
                        }
                    }
                    // Store fingerprints received from devices with not yet authenticated keys for later authentication or revocation.
                    else {
                        if (cacheTrustMessageData(xmppConnectionService, keysOwner, uri.getFingerprintsForAuthentication(), message.getFingerprint(), true)) {
                            infoMessageBody = createInfoMessageBody(keysOwner, infoMessageBody, true, false);
                        }
                        if (cacheTrustMessageData(xmppConnectionService, keysOwner, uri.getFingerprintsForRevocation(), message.getFingerprint(), false)) {
                            infoMessageBody = createInfoMessageBody(keysOwner, infoMessageBody, false, false);
                        }
                    }

                    if (infoMessageBody == null) {
                        // TODO Would it be better to delete the message and do not show anything?
                        // TODO If yes, how could it be deleted?
                        infoMessageBody = "You received a message for automatic OMEMO key authentication or revocation but it had no effect.";
                    }

                    message.setBody(infoMessageBody);
                    message.setType(Message.TYPE_ATT_INFO);
                }
                return true;
            }
        }
        return false;
    }

    // TODO Delete after testing.
    private static boolean authenticateInit(XmppConnectionService xmppConnectionService, Contact keysOwner, List<XmppUri.Fingerprint> fingerprints) {
        return xmppConnectionService.authenticateKeys(keysOwner, fingerprints, true, true);
    }

    /**
     * Authenticates the keys of a given key owner by their given fingerprints.
     * 
     * @param xmppConnectionService Service of the XMPP connection.
     * @param keysOwner Owner of the keys which have the given fingerprints.
     * @param fingerprints Fingerprints used for authentication.
     * @return true if at least one of the given keys has been authenticated or will get automatically authenticated after it has been fetched, otherwise false.
     */
    private static boolean authenticate(XmppConnectionService xmppConnectionService, Contact keysOwner, List<XmppUri.Fingerprint> fingerprints) {
        return xmppConnectionService.authenticateKeys(keysOwner, fingerprints, true, false);
    }

    /**
     * Revokes the keys of a given key owner by their given fingerprints.
     * 
     * @param keysOwner Owner of the keys which have the given fingerprints.
     * @param fingerprints Fingerprints used for revocation.
     * @return true if the trust in at least one of the given keys has been revoked, otherwise false.
     */
    private static boolean revoke(Contact keysOwner, List<XmppUri.Fingerprint> fingerprints) {
        return keysOwner.getAccount().getAxolotlService().distrustKeys(keysOwner, fingerprints, false);
    }

    /**
     * Stores the data of a trust message which was sent from a device with a not yet authenticated key.
     * The information of the trust message will be used by {@link #cacheTrustMessageData} after the authentication of the device's key.
     *
     * @param xmppConnectionService Service of the XMPP connection.
     * @param keysOwner Owner of the keys which have the given fingerprints.
     * @param fingerprints Fingerprints used for authentication or revocation.
     * @param senderFingerprint Fingerprint of the sender's key of the trust message which not led to an authentication because the sender's key is not yet authenticated.
     * @param trust Trust in the keys with the given fingerprints. true for authentication and false for revocation.
     * @return true if at least one fingerprint has been stored.
     */
    private static boolean cacheTrustMessageData(XmppConnectionService xmppConnectionService, Contact keysOwner, List<XmppUri.Fingerprint> fingerprints, String senderFingerprint, boolean trust) {
        AxolotlService axolotlService = keysOwner.getAccount().getAxolotlService();
        boolean trustMessageFingerprintsStored = false;

        for (XmppUri.Fingerprint fp : fingerprints) {
            String fingerprintWithVersion = axolotlService.createFingerprintWithVersion(fp.fingerprint);
            FingerprintStatus fingerprintStatus = axolotlService.getFingerprintTrust(fingerprintWithVersion);
            if (fingerprintStatus == null || !fingerprintStatus.isVerified()) {
                if (xmppConnectionService.databaseBackend.cacheTrustMessageData(keysOwner, fingerprintWithVersion, senderFingerprint, trust)) {
                    trustMessageFingerprintsStored = true;
                }
            }
        }

        return trustMessageFingerprintsStored;
    }

    /**
     * Authenticates keys or revokes the trust in keys for whom earlier trust messages were received but not used for authentication or revocation at that time.
     * The information of the trust message was cached by {@link #cacheTrustMessageData} in the database to be used by this method.
     * 
     * @param xmppConnectionService Service of the XMPP connection.
     * @param account Account of the user who wants to do the authentication or revocation.
     * @param senderFingerprint Fingerprint of the sender of former trust messages which may not led to an authentication or revocation because the sender's key was not yet authenticated but now it is.
     */
    public static void authenticateOrRevokeWithCachedTrustMessageData(XmppConnectionService xmppConnectionService, Account account, String senderFingerprint) {
        authenticateWithCachedTrustMessageData(xmppConnectionService, account, senderFingerprint);
        revokeWithCachedTrustMessageData(xmppConnectionService, account, senderFingerprint);
    }

    /**
     * Revokes the trust in keys for whom earlier revocation messages were received but not used for revocation at that time.
     * The information of the revocation message was cached by {@link #cacheTrustMessageData} in the database to be used by this method.
     *
     * @param xmppConnectionService Service of the XMPP connection.
     * @param account Account of the user who wants to do the revocation.
     * @param senderFingerprint Fingerprint of the sender of former revocation messages which may not led to a revocation because the sender's key was not yet authenticated but now it is.
     */
    private static void authenticateWithCachedTrustMessageData(XmppConnectionService xmppConnectionService, Account account, String senderFingerprint) {
        authenticateOrRevokeWithCachedTrustMessageData(xmppConnectionService, account, senderFingerprint, true);
    }

    /**
     * Authenticates keys for whom earlier authentication messages were received but not used for authentication at that time.
     * The information of the authentication message was cached by {@link #cacheTrustMessageData} in the database to be used by this method.
     *
     * @param xmppConnectionService Service of the XMPP connection.
     * @param account Account of the user who wants to do the authentication.
     * @param senderFingerprint Fingerprint of the sender of former authentication messages which may not led to an authentication because the sender's key was not yet authenticated but now it is.
     */
    private static void revokeWithCachedTrustMessageData(XmppConnectionService xmppConnectionService, Account account, String senderFingerprint) {
        authenticateOrRevokeWithCachedTrustMessageData(xmppConnectionService, account, senderFingerprint, false);
    }

    /**
     * Authenticates keys or revokes the trust in keys for whom earlier trust messages were received but not used for authentication or revocation at that time.
     * The information of the trust message was cached by {@link #cacheTrustMessageData} in the database to be used by this method.
     *
     * @param xmppConnectionService Service of the XMPP connection.
     * @param account Account of the user who wants to do the authentication or revocation.
     * @param senderFingerprint Fingerprint of the sender of former trust messages which may not led to an authentication or revocation because the sender's key was not yet authenticated but now it is.
     * @param trust Trust in the keys for whom their fingerprints should be loaded. true for fingerprints cached for authentication and false for fingerprints cached for revocation.
     */
    private static void authenticateOrRevokeWithCachedTrustMessageData(XmppConnectionService xmppConnectionService, Account account, String senderFingerprint, boolean trust) {
        AxolotlService axolotlService = account.getAxolotlService();

        for (Map.Entry<String, List<String>> keysOwnerAndFingerprints : xmppConnectionService.databaseBackend.loadTrustMessageData(account, account.getAxolotlService().createFingerprintWithVersion(senderFingerprint), trust).entrySet()) {
            Contact keysOwner = account.getRoster().getContact(Jid.of(keysOwnerAndFingerprints.getKey()));
            List<XmppUri.Fingerprint> fingerprintsForAuthenticationOrRevocation = new ArrayList<>();

            for (String fingerprint : keysOwnerAndFingerprints.getValue()) {
                fingerprintsForAuthenticationOrRevocation.add(keysOwner.getFingerprintForFingerprintString(axolotlService.createFingerprintWithoutVersion(fingerprint)));
                xmppConnectionService.databaseBackend.deleteTrustMessageDataForFingerprint(account, fingerprint);
            }
            if (trust) {
                authenticate(xmppConnectionService, keysOwner, fingerprintsForAuthenticationOrRevocation);
            } else {
                revoke(keysOwner, fingerprintsForAuthenticationOrRevocation);
            }
        }
    }

    /**
     *  Sends authentication messages to own devices and devices of contacts.
     * 
     * @param xmppConnectionService Service of the XMPP connection.
     * @param keysOwner Owner of the keys which have the given fingerprints.
     * @param fingerprints Fingerprints used for authentication.
     */
    public static void sendAuthenticationMessage(XmppConnectionService xmppConnectionService, Contact keysOwner, List<XmppUri.Fingerprint> fingerprints) {
        sendTrustMessages(xmppConnectionService, keysOwner, fingerprints, true);
    }

    /**
     * Sends revocation messages to own devices and devices of contacts.
     * 
     * @param xmppConnectionService Service of the XMPP connection.
     * @param keysOwner Owner of the keys which have the given fingerprints.
     * @param fingerprints Fingerprints used for revocation.
     */
    public static void sendRevocationMessages(XmppConnectionService xmppConnectionService, Contact keysOwner, List<XmppUri.Fingerprint> fingerprints) {
        sendTrustMessages(xmppConnectionService, keysOwner, fingerprints, false);
    }

    /**
     * Sends trust messages to own devices with authenticated keys and to devices of contacts with authenticated keys.
     * 
     * @param xmppConnectionService Service of the XMPP connection.
     * @param keysOwner Owner of the keys which have the given fingerprints.
     * @param fingerprints Fingerprints used for authentication or revocation.
     * @param trust Trust in the given fingerprints. true for authentication and false for revocation.
     */
    private static void sendTrustMessages(XmppConnectionService xmppConnectionService, Contact keysOwner, List<XmppUri.Fingerprint> fingerprints, boolean trust) {
        Account account = keysOwner.getAccount();
        Contact ownAccountAsContact = account.getSelfContact();

        // Send trust messages containing the own keys which have been authenticated or for whom the trust has been revoked
        // to the own devices with already authenticated keys
        // and to all contacts with already authenticated keys.
        // Since all keys which are not already authenticated become untrusted after the first authentication, there is only a check for at least one authenticated key necessary.
        if (keysOwner.isSelf()) {
            List<Contact> contacts = account.getRoster().getContacts();

            // Use XEP-0280 Message Carbons to deliver trust messages to own devices while not sending a separate message to the own account.
            boolean deliveredViaMessageCarbons = false;
            for (Contact contact : contacts) {
                if (!contact.isSelf()) {
                    // TODO Encrypt trust messages not for own devices with unauthenticated keys when using Message Carbons.

                    // Send a trust message containing the own keys which have been authenticated or for whom the trust has been revoked to the contact's devices with already authenticated keys.
                    if (contact.hasAuthenticatedKeys()) {
                        sendTrustMessage(xmppConnectionService, ownAccountAsContact, fingerprints, trust, contact);
                        deliveredViaMessageCarbons = true;
                    }
                    // Send an authentication message containing the already authenticated contact's keys to the own devices with already authenticated keys.
                    // Thus, the device whose key has been authenticated gets trust messages for already authenticated contact's keys.
                    if (ownAccountAsContact.hasAuthenticatedKeys() && trust) {
                        sendTrustMessage(xmppConnectionService, contact, contact.getFingerprintsOfAuhtenticatedAndActiveKeys(), true, ownAccountAsContact);
                    }
                }
            }
            // If there are no contacts with authenticated keys and more other own devices than those of which the keys have been authenticated, a trust message is explicitly sent to the own account.
            if (!deliveredViaMessageCarbons && fingerprints.size() < ownAccountAsContact.getFingerprintsOfAuthenticatedKeys().size()) {
                sendTrustMessage(xmppConnectionService, ownAccountAsContact, fingerprints, trust, ownAccountAsContact);
            }
        } else {
            // Send a trust message containing the contact's keys which have been authenticated or for whom the trust has been revoked to the own devices with already authenticated keys.
            if (ownAccountAsContact.hasAuthenticatedKeys()) {
                sendTrustMessage(xmppConnectionService, keysOwner, fingerprints, trust, ownAccountAsContact);
            }
            // Send an authentication message containing the own already authenticated keys to the contact whose keys have been authenticated.
            if (keysOwner.hasAuthenticatedKeys() && trust)
                sendTrustMessage(xmppConnectionService, ownAccountAsContact, ownAccountAsContact.getFingerprintsOfAuhtenticatedAndActiveKeys(), true, keysOwner);
        }
    }

    /**
     * Sends a trust message to all devices with already authenticated keys of a given recipient.
     * 
     * @param xmppConnectionService Service of the XMPP connection.
     * @param keysOwner Owner of the keys which have the given fingerprints.
     * @param fingerprints Fingerprints used for authentication or revocation.
     * @param trust Trust in the given fingerprints. true for authentication and false for revocation.
     * @param recipient Recipient of the trust message.
     */
    private static void sendTrustMessage(XmppConnectionService xmppConnectionService, Contact keysOwner, List<XmppUri.Fingerprint> fingerprints, boolean trust, Contact recipient) {
        if (!fingerprints.isEmpty()) {
            sendMessage(xmppConnectionService, recipient, createTrustMessageBody(keysOwner, fingerprints, trust));
        }
    }

    /**
     * Sends a message with a given body to a given recipient.
     * 
     * @param xmppConnectionService Service of the XMPP connection.
     * @param recipient Recipient of the message.
     * @param body Body of the message.
     */
    private static void sendMessage(XmppConnectionService xmppConnectionService, Contact recipient, String body) {
        Conversation conversation = xmppConnectionService.findOrCreateConversation(recipient.getAccount(), recipient.getJid(), false, true);
        Message message = new Message(conversation, body, conversation.getNextEncryption());
        xmppConnectionService.sendMessage(message);
    }

    /**
     * Creates the body of a trust message in the format of an XMPP URI specified in XEP-0147.
     *
     * That body is an XMPP URI with "omemo-trust" as the action.
     * As key-value pairs there are "auth=<fingerprint>" and "revoke=<fingerprint>".
     * At least one of those key-value pairs is required.
     * The same key MAY occur in multiple key-value pairs.
     * The same value MUST NOT occur in multiple key-value pairs.
     *
     * Example: "xmpp:user@example.org?omemo-trust;auth=623548d3835c6d33ef5cb680f7944ef381cf712bf23a0119dabe5c4f252cd02f;auth=d9f849b6b828309c5f2c8df4f38fd891887da5aaa24a22c50d52f69b4a80817e;revoke=b423f5088de9a924d51b31581723d850c7cc67d0a4fe6b267c3d301ff56d2413".
     *
     * Notice: This has currently not the same format as for manual key authentication like with
     * "xmpp:user@example.org?omemo-sid-4133529726=623548d3835c6d33ef5cb680f7944ef381cf712bf23a0119dabe5c4f252cd02f;omemo-sid-3818107560=d9f849b6b828309c5f2c8df4f38fd891887da5aaa24a22c50d52f69b4a80817e".
     * 
     * @param keysOwner Owner of the keys whose fingerprints will be inserted into the trust message.
     * @param fingerprints Fingerprints used for authentication or revocation.
     * @param trust Trust in the given fingerprints. true for authentication or false for revocation.
     * @return Body of a trust message.
     */
    private static String createTrustMessageBody(Contact keysOwner, List<XmppUri.Fingerprint> fingerprints, boolean trust) {
        String trustMessageBody = "xmpp:" + keysOwner.getBareJid() + "?" + ACTION_TRUST;
        String actionKey = trust ? ACTION_KEY_AUTHENTICATE : ACTION_KEY_REVOKE;
        for (XmppUri.Fingerprint fingerprint : fingerprints) {
            trustMessageBody += ";" + actionKey + "=" + fingerprint.fingerprint;
        }
        return trustMessageBody;
    }

    /**
     * Creates the body of an info message to be shown to the user.
     * @param keysOwner Owner of the keys which might have been authenticated or revoked.
     * @param prefix Prefix to be prepended to the info message body.
     * @param trust true for authentication and false for revocation.
     * @param executed true if the authentication or revocation has been executed or false otherwise.
     * @return Body of an info message.
     */
    private static String createInfoMessageBody(Contact keysOwner, String prefix, boolean trust, boolean executed) {
        String infoMessageBody;

        if (trust) {
            String infoStub = "OMEMO keys of " + keysOwner.getBareJid() + " ";
            if (executed) {
                infoMessageBody = infoStub + "have been automatically authenticated or will get automatically authenticated after they have been fetched.";
            } else {
                infoMessageBody = infoStub + "will get automatically authenticated as soon as the key used for this message gets authenticated.";
            }
        } else {
            String infoStub = "The trust in OMEMO keys of " + keysOwner.getBareJid() + " ";
            if (executed) {
                infoMessageBody = infoStub + "has been automatically revoked.";
            } else {
                // TODO Should this (future revocation) be possible?
                infoMessageBody = infoStub + "will get automatically revoked as soon as the key used for this message gets authenticated.";
            }
        }

        // Add a prefix and an empty line to the beginning of infoMessageBody if a prefix is given.
        if (prefix != null && !prefix.isEmpty()) {
            infoMessageBody = prefix + "\n\n" + infoMessageBody;
        }

        return infoMessageBody;
    }
}