package eu.siacs.conversations.ui.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ImageView;

import java.util.Arrays;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.MessageReferenceBinding;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.ConversationFragment;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.utils.UIHelper;

public class MessageReferenceUtils {

    /**
     * Hide the whole area where a referenced message would be displayed.
     * @param messageReferenceBinding data binding that holds the message reference views
     */
    public static void hideMessageReference(MessageReferenceBinding messageReferenceBinding) {
        messageReferenceBinding.messageReferenceContainer.setVisibility(View.GONE);
        messageReferenceBinding.messageReferenceText.setVisibility(View.GONE);
        messageReferenceBinding.messageReferenceIcon.setVisibility(View.GONE);
        messageReferenceBinding.messageReferenceImageThumbnail.setVisibility(View.GONE);
        messageReferenceBinding.messageReferencePreviewCancelButton.setVisibility(View.GONE);
    }

    /**
     * Displays the message reference area.
     * @param position position of the given message in the array
     * @param messageReferenceBinding binding that was created for the messageReference
     * @param message message that has a messageReference or null if the messageReference is used for a preview before sending a new message with that messageReference
     * @param referencedMessage message that is referenced by the given message
     * @param darkBackground true if the background (message bubble) of the given message is dark
     */
    public static void displayMessageReference(final XmppActivity activity, final int position, final MessageReferenceBinding messageReferenceBinding, final Message message, final Message referencedMessage, boolean darkBackground) {
        // true if this method is used for a preview of a messageReference area
        boolean messageReferencePreview = message == null;

        if (darkBackground) {
            // Use different backgrounds depending on the usage of a message bubble.
            // A messageReference (MessageAdapter) of a normal message is inside of a message bubble.
            // A messageReferencePreview (ConversationFragment) is not inside a message bubble.
            int background;
            if (!messageReferencePreview) {
                background = R.drawable.message_reference_background_white;
            } else {
                background = R.drawable.message_reference_background_dark_grey;
                messageReferenceBinding.messageReferencePreviewCancelButton.setBackground(activity.getResources().getDrawable(R.drawable.ic_send_cancel_offline_white));
            }
            messageReferenceBinding.messageReferenceContainer.setBackground(activity.getResources().getDrawable(background));

            messageReferenceBinding.messageReferenceBar.setBackgroundColor(activity.getResources().getColor(R.color.white70));
            messageReferenceBinding.messageReferenceInfo.setTextAppearance(activity, R.style.TextAppearance_Conversations_Caption_OnDark);
            messageReferenceBinding.messageReferenceText.setTextAppearance(activity, R.style.TextAppearance_Conversations_MessageReferenceText_OnDark);
        } else if (messageReferencePreview) {
            // Set a different background if the background is not dark and the messageReference is for a preview.
            messageReferenceBinding.messageReferenceContainer.setBackground(activity.getResources().getDrawable(R.drawable.message_reference_background_light_grey));
        }

        messageReferenceBinding.messageReferenceContainer.setVisibility(View.VISIBLE);

        if (referencedMessage == null) {
            messageReferenceBinding.messageReferenceInfo.setVisibility(View.VISIBLE);
            messageReferenceBinding.messageReferenceInfo.setText(activity.getResources().getString(R.string.message_not_found));
            return;
        } else if (referencedMessage.isFileOrImage() && !activity.xmppConnectionService.getFileBackend().getFile(referencedMessage).exists()) {
            messageReferenceBinding.messageReferenceIcon.setVisibility(View.VISIBLE);
            setMessageReferenceIcon(darkBackground, messageReferenceBinding.messageReferenceIcon, activity.getResources().getDrawable(R.drawable.ic_file_deleted), activity.getResources().getDrawable(R.drawable.ic_file_deleted_white));
        } else if (referencedMessage.isImageOrVideo()) {
            displayReferencedImageMessage(activity, messageReferenceBinding, message, referencedMessage);
        } else if (referencedMessage.isAudio()) {
            messageReferenceBinding.messageReferenceIcon.setVisibility(View.VISIBLE);
            setMessageReferenceIcon(darkBackground, messageReferenceBinding.messageReferenceIcon, activity.getResources().getDrawable(R.drawable.ic_attach_record), activity.getResources().getDrawable(R.drawable.ic_attach_record_white));
        } else if (referencedMessage.isGeoUri()) {
            messageReferenceBinding.messageReferenceIcon.setVisibility(View.VISIBLE);
            setMessageReferenceIcon(darkBackground, messageReferenceBinding.messageReferenceIcon, activity.getResources().getDrawable(R.drawable.ic_attach_location), activity.getResources().getDrawable(R.drawable.ic_attach_location_white));
        } else if (referencedMessage.treatAsDownloadable()) {
            messageReferenceBinding.messageReferenceIcon.setVisibility(View.VISIBLE);
            setMessageReferenceIcon(darkBackground, messageReferenceBinding.messageReferenceIcon, activity.getResources().getDrawable(R.drawable.ic_file_download), activity.getResources().getDrawable(R.drawable.ic_file_download_white));
        } else if (referencedMessage.isText()) {
            messageReferenceBinding.messageReferenceText.setVisibility(View.VISIBLE);
            messageReferenceBinding.messageReferenceText.setText(extractFirstTwoLinesOfBody(referencedMessage));
        } else {
            messageReferenceBinding.messageReferenceIcon.setVisibility(View.VISIBLE);
            // default icon
            setMessageReferenceIcon(darkBackground, messageReferenceBinding.messageReferenceIcon, activity.getResources().getDrawable(R.drawable.ic_attach_document), activity.getResources().getDrawable(R.drawable.ic_attach_document_white));
        }

        messageReferenceBinding.messageReferenceInfo.setText(MessageReferenceUtils.createInfo(activity, activity, referencedMessage));

        final Conversation conversation = (Conversation) referencedMessage.getConversation();

        if (messageReferencePreview) {
            messageReferenceBinding.messageReferencePreviewCancelButton.setVisibility(View.VISIBLE);

            // Cancel the referencing of a message.
            messageReferenceBinding.messageReferencePreviewCancelButton.setOnClickListener(v -> {
                hideMessageReference(messageReferenceBinding);
                conversation.setMessageReference(null);
                conversation.getConversationFragment().updateChatMsgHint();
            });

            // Jump to the referenced message when the message reference preview is clicked.
            messageReferenceBinding.messageReferenceContainer.setOnClickListener(v -> {
                conversation.getConversationFragment().setSelection(position, false);
            });
        } else {
            // Jump to the referenced message when the message reference is clicked.
            messageReferenceBinding.messageReferenceContainer.setOnClickListener(v -> {
                final ConversationFragment conversationFragment = conversation.getConversationFragment();
                if (position == -1) {
                    activity.xmppConnectionService.loadMoreMessages(referencedMessage, conversationFragment.getOnMoreMessagesLoadedImpl(conversationFragment.getView().findViewById(R.id.messages_view), referencedMessage));
                } else {
                    conversationFragment.setSelection(position, false);
                }
            });
        }
    }

    /**
     * Creates an info text that contains the sender and the date for a given message.
     * @param message message for that the info text is generated
     * @return info text
     */
    public static String createInfo(XmppActivity activity, Context context, Message message) {
        // text that is shown when the referenced message is no image or video
        String info;

        // Set the name of the author of the referenced message as the tag.
        info = UIHelper.getMessageDisplayName(message);

        // Replace the name of the author with a standard identifier for the user if the user is the author of the referenced message.
        if (info.equals(((Conversation) message.getConversation()).getMucOptions().getSelf().getName())) {
            info = activity.getString(R.string.me);
        }

        // Add the time when the referenced message was sent to the tag.
        info += "\n" + UIHelper.readableTimeDifferenceFull(context, message.getMergedTimeSent());

        return info;
    }

    /**
     * Sets the image for the message reference icon depending on the color of its background.
     * @param darkBackground specifies if the background of the message reference icon is dark
     * @param messageReferenceIcon icon for the message reference
     * @param defaultDrawable drawable that will be used as the message reference icon if its background is light
     * @param drawableForDarkBackground drawable that will be used as the message reference icon if its background is dark
     */
    public static void setMessageReferenceIcon(boolean darkBackground, ImageView messageReferenceIcon, Drawable defaultDrawable, Drawable drawableForDarkBackground) {
        if (darkBackground) {
            messageReferenceIcon.setBackground(drawableForDarkBackground);
        } else {
            messageReferenceIcon.setBackground(defaultDrawable);
        }
    }

    /**
     * Displays a thumbnail for the image or video of the referenced message.
     * The normal message is used for showing the image thumbnail if a message exists
     * which is the case when this method is called by MessageAdapter.
     * Otherwise use the referenced message which is the case when the calling method is called by ConversationFragment.
     */
    public static void displayReferencedImageMessage(final XmppActivity activity, final MessageReferenceBinding messageReferenceBinding, Message message, final Message referencedMessage) {
        if (message != null) {
            // Find the relative file path for the referenced image or video.
            if (message.getRelativeFilePath() == null) {
                message.setRelativeFilePath(referencedMessage.getRelativeFilePath());
            }
        } else {
            // Set the message as the referenced message so that it can be used for loading the bitmap.
            message = referencedMessage;

            // Set the scale type manually only for the message reference preview since a common scale type cannot be used.
            messageReferenceBinding.messageReferenceImageThumbnail.setScaleType(ImageView.ScaleType.FIT_START);

            // Remove the rounded corners of the thumbnail in the message reference preview.
            messageReferenceBinding.messageReferenceImageThumbnail.setCornerRadius(0);
        }

        activity.loadBitmapForReferencedImageMessage(message, messageReferenceBinding.messageReferenceImageThumbnail);
        messageReferenceBinding.messageReferenceImageThumbnail.setVisibility(View.VISIBLE);
    }

    /**
     * Creates a string with newlines for each string of a given array of strings.
     * @param allLines string array that contains all strings
     * @param indexOfFirstLineToTake position (inclusive) of the first string to be taken for the newly created string
     * @param indexOfLastLineToTake position (exclusive) of the last string to be taken for the newly created string
     * @return string with the desired lines
     */
    public static String createStringWithLinesOutOfStringArray(String[] allLines, int indexOfFirstLineToTake, int indexOfLastLineToTake) {
        StringBuilder takingBuilder = new StringBuilder();
        for (String line : Arrays.copyOfRange(allLines, indexOfFirstLineToTake, indexOfLastLineToTake)) {
            takingBuilder.append("\n" + line);
        }

        // Delete the first newline ("\n") because it is not needed.
        takingBuilder.delete(0, 1);

        return takingBuilder.toString();
    }

    /**
     * Extracts the first lines of a message's body.
     * This can be used to show only a specific number of body lines in the message reference text view
     * since the truncation with "ellipsize" and "maxLines" does not adjust the width for the first lines.
     * Instead the width is adjusted for the longest line even if that line will not be displayed
     * and this leaves an space on the right side.
     * @param message message for that the first lines of its body will be extracted
     * @param linesToBeExtracted number of lines to be extracted
     * @return extracted lines
     */
    public static String extractFirstLinesOfBody(Message message, int linesToBeExtracted) {
        String[] bodyLines = message.getBody().split("\n");

        // Reduce the number of lines to be extracted if the body has less lines than that number.
        if (linesToBeExtracted > bodyLines.length) {
            linesToBeExtracted = bodyLines.length;
        }

        String firstLinesOfBody = createStringWithLinesOutOfStringArray(bodyLines, 0, linesToBeExtracted);

        if (linesToBeExtracted < bodyLines.length) {
            firstLinesOfBody += "...";
        }

        return firstLinesOfBody;
    }

    /**
     * Extracts the first two lines of a message's body.
     * @param message message for that the first lines of its body will be extracted
     * @return extracted lines
     */
    public static String extractFirstTwoLinesOfBody(Message message) {
        return extractFirstLinesOfBody(message, 2);
    }
}