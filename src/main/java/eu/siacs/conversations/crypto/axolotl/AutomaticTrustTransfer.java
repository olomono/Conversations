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
     * Column name for the key fingerprint of a sender of an authentication message which could not be used for authentication directly.
     */
    public static final String SENDER_AUTHENTICATION_MESSAGE = "sender_auth";

    /**
     * Column name for the key fingerprint of a sender of an revocation message which could not be used for revocation directly.
     */
    public static final String SENDER_REVOCATION_MESSAGE = "sender_revoke";

    /**
     * Authenticates a key or revokes the trust in a key if the given message contains a URI for trust messages.
     * 
     * @param xmppConnectionService Service of the XMPP connection.
     * @param message Message which may be a trust message.
     * @return true if the given message is a valid trust message.
     */
    public static boolean authenticateOrRevoke(XmppConnectionService xmppConnectionService, Message message) {
        if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
            XmppUri uri = new XmppUri(message.getBody().trim());
            if (uri.isAction(ACTION_TRUST)) {
                Contact sender = message.getContact();
                Contact keysOwner = message.getContact().getAccount().getRoster().getContact(Jid.of(uri.getJid()));

                // TODO Delete after testing.
                authenticateInit(xmppConnectionService, keysOwner, uri.getFingerprintsForInitialization());

                // A trust messages from an own device may authenticate or revoke the keys of all own devices and devices of contacts.
                // Whereas trust messages from devices of contacts may only authenticate or revoke the keys of their own devices.
                if ((sender.isSelf() || keysOwner.equals(sender))) {
                    if (message.getConversation().getAccount().getAxolotlService().getFingerprintTrust(message.getFingerprint()).isVerified()) {
                        if (authenticate(xmppConnectionService, keysOwner, uri.getFingerprintsForAuthentication())) {
                            message.setErrorMessage("error");
                            message.setBody("body");
                        }
                        revoke(keysOwner, uri.getFingerprintsForRevocation());
                    } else {
                        // Store fingerprints received from devices with not yet authenticated keys.
                        storeTrustMessageFingerprints(xmppConnectionService, keysOwner, uri.getFingerprintsForAuthentication(), message.getFingerprint(), true);
                        storeTrustMessageFingerprints(xmppConnectionService, keysOwner, uri.getFingerprintsForRevocation(), message.getFingerprint(), false);
                    }
                    xmppConnectionService.updateMessage(message);
                    return true;
                }
            }
        }
        return false;
    }

    // TODO Delete after testing.
    private static void authenticateInit(XmppConnectionService xmppConnectionService, Contact keysOwner, List<XmppUri.Fingerprint> fingerprints) {
        xmppConnectionService.verifyFingerprints(keysOwner, fingerprints, false, true);
    }

    /**
     * Authenticates the keys of a given key owner by their given fingerprints.
     * 
     * @param xmppConnectionService Service of the XMPP connection.
     * @param keysOwner Owner of the keys which have the given fingerprints.
     * @param fingerprints Fingerprints used for authentication.
     */
    private static boolean authenticate(XmppConnectionService xmppConnectionService, Contact keysOwner, List<XmppUri.Fingerprint> fingerprints) {
        return xmppConnectionService.verifyFingerprints(keysOwner, fingerprints, false, false);
    }

    /**
     * Revokes the keys of a given key owner by their given fingerprints.
     * 
     * @param keysOwner Owner of the keys which have the given fingerprints.
     * @param fingerprints Fingerprints used for revocation.
     */
    private static void revoke(Contact keysOwner, List<XmppUri.Fingerprint> fingerprints) {
        keysOwner.getAccount().getAxolotlService().distrustFingerprints(keysOwner, fingerprints, false);
    }

    /**
     * Stores the fingerprints of a trust message from devices with not yet authenticated keys.
     * 
     * @param xmppConnectionService Service of the XMPP connection.
     * @param keysOwner Owner of the keys which have the given fingerprints.
     * @param fingerprints Fingerprints used for authentication or revocation.
     * @param senderFingerprint Fingerprint of the sender of the trust message which not led to an authentication because the sender's key is not yet authenticated.
     * @param trust Trust in the given fingerprints. true for authentication and false for revocation.
     */
    private static void storeTrustMessageFingerprints(XmppConnectionService xmppConnectionService, Contact keysOwner, List<XmppUri.Fingerprint> fingerprints, String senderFingerprint, boolean trust) {
        AxolotlService axolotlService = keysOwner.getAccount().getAxolotlService();

        for (XmppUri.Fingerprint fp : fingerprints) {
            String fingerprintWithVersion = axolotlService.createFingerprintWithVersion(fp.fingerprint);
            if (!axolotlService.getFingerprintTrust(fingerprintWithVersion).isVerified()) {
                xmppConnectionService.databaseBackend.storeTrustMessageFingerprint(keysOwner, fingerprintWithVersion, senderFingerprint, trust);
            }
        }
    }

    /**
     * Authenticates the keys for whom earlier authentication messages were received but not used for authentication at that time.
     * The information of the authentication message was stored in the database to be used by this method.
     * 
     * @param xmppConnectionService Service of the XMPP connection.
     * @param account XMPP account of the user who wants to do this authentication.
     * @param senderFingerprint Fingerprint of the sender of a former authentication message which may not led to an authentication because the sender's key was not yet authenticated.
     */
    public static void authenticateWithStoredTrustMessageFingerprint(XmppConnectionService xmppConnectionService, Account account, String senderFingerprint) {
        // TODO Implement ability to store and query multiple trust message fingerprints in DB.
        // TODO When to delete a stored trust message fingerprint from DB?
        // TODO Only for authentication or also for revocation?
        // TODO What to do when revocation comes first and auth afterwards and vice versa?

        AxolotlService axolotlService = account.getAxolotlService();

        for (Map.Entry<String, List<String>> keysOwnerAndFingerprints : xmppConnectionService.databaseBackend.loadTrustMessageFingerprints(account, account.getAxolotlService().createFingerprintWithVersion(senderFingerprint), true).entrySet()) {
            Contact keysOwner = account.getRoster().getContact(Jid.of(keysOwnerAndFingerprints.getKey()));
            List<XmppUri.Fingerprint> fingerprintsForAuthentication = new ArrayList<>();

            for (String fingerprint : keysOwnerAndFingerprints.getValue()) {
                XmppUri.Fingerprint fingerprintForAuthentication = keysOwner.getFingerprintForFingerprintString(axolotlService.createFingerprintWithoutVersion(fingerprint));
                if (fingerprintForAuthentication != null) {
                    fingerprintsForAuthentication.add(fingerprintForAuthentication);
                    xmppConnectionService.databaseBackend.deleteTrustMessageFingerprint(keysOwner, fingerprint, true);
                }
            }
            authenticate(xmppConnectionService, keysOwner, fingerprintsForAuthentication);
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
     * Sends trust messages to own devices and devices of contacts.
     * 
     * @param xmppConnectionService Service of the XMPP connection.
     * @param keysOwner Owner of the keys which have the given fingerprints.
     * @param fingerprints Fingerprints used for authentication or revocation.
     * @param trust Trust in the given fingerprints. true for authentication and false for revocation.
     */
    private static void sendTrustMessages(XmppConnectionService xmppConnectionService, Contact keysOwner, List<XmppUri.Fingerprint> fingerprints, boolean trust) {
        Account account = keysOwner.getAccount();
        Contact ownAccountAsContact = account.getSelfContact();

        // TODO send trust messages only to devices with authenticated keys not with just blindly trusted keys.

        // Send trust messages containing the own keys which have been authenticated
        // to the own devices with already authenticated keys
        // and to all contacts with already authenticated keys.
        if (keysOwner.isSelf()) {
            List<Contact> contacts = account.getRoster().getContacts();

            // Use XEP-0280 Message Carbons to deliver trust messages to own devices while not sending a separate message to the own account.
            boolean deliveredViaMessageCarbons = false;
            for (Contact contact : contacts) {
                if (!contact.isSelf()) {
                    // Send a trust message containing the own keys which have been authenticated to the contact's devices with already authenticated keys.
                    if (contact.hasVerifiedKeys()) {
                        sendTrustMessage(xmppConnectionService, ownAccountAsContact, fingerprints, trust, contact);
                        deliveredViaMessageCarbons = true;
                    }
                    // Send a trust message containing the already authenticated contact's keys to the own devices with already authenticated keys.
                    if (ownAccountAsContact.hasVerifiedKeys()) {
                        sendTrustMessage(xmppConnectionService, contact, contact.getVerifiedAndActiveFingerprints(), trust, ownAccountAsContact);
                    }
                }
            }
            // If there are no contacts with authenticated keys and more other own devices than those of which the keys have been authenticated, a trust message is explicitly sent to the own account.
            if (!deliveredViaMessageCarbons && fingerprints.size() < ownAccountAsContact.getVerifiedFingerprints().size()) {
                sendTrustMessage(xmppConnectionService, ownAccountAsContact, fingerprints, trust, ownAccountAsContact);
            }
        }
        else if (trust){
            // Send an authentication message containing the contact's keys which have been authenticated to the own devices with already authenticated keys.
            if (ownAccountAsContact.hasVerifiedKeys()) {
                sendTrustMessage(xmppConnectionService, keysOwner, fingerprints, true, ownAccountAsContact);
            }
            // Send an authentication message containing the own already authenticated keys to the contact whose keys have been authenticated.
            if (keysOwner.hasVerifiedKeys())
            sendTrustMessage(xmppConnectionService, ownAccountAsContact, ownAccountAsContact.getVerifiedAndActiveFingerprints(), true, keysOwner);
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
            sendMessage(xmppConnectionService, recipient, createTrustMessageBody(keysOwner.getJid(), fingerprints, trust));
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
     * @param jid JID of the communication partner whose fingerprints will be inserted into the trust message.
     * @param fingerprints Fingerprints used for authentication or revocation.
     * @param trust Trust in the given fingerprints. true for authentication and false for revocation.
     * @return Body of a trust message.
     */
    private static String createTrustMessageBody(Jid jid, List<XmppUri.Fingerprint> fingerprints, boolean trust) {
        String trustMessageBody = "xmpp:" + jid.asBareJid().toEscapedString() + "?" + ACTION_TRUST;
        for (XmppUri.Fingerprint fingerprint : fingerprints) {
            trustMessageBody += ";" + (trust ? ACTION_KEY_AUTHENTICATE : ACTION_KEY_REVOKE) + "=" + fingerprint.fingerprint;
        }
        return trustMessageBody;
    }
}
