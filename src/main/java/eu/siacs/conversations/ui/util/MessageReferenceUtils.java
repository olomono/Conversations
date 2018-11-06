package eu.siacs.conversations.ui.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import java.util.Arrays;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.utils.UIHelper;

public class MessageReferenceUtils {

    /**
     * Hide the whole area where a referenced message would be displayed.
     */
    public static void hideMessageReference(MessageViewHolder viewHolder) {
        viewHolder.getMessageReferenceContainer().setVisibility(View.GONE);
        viewHolder.getMessageReferenceBar().setVisibility(View.GONE);
        viewHolder.getMessageReferenceInfo().setVisibility(View.GONE);
        viewHolder.getMessageReferenceText().setVisibility(View.GONE);
        viewHolder.getMessageReferenceIcon().setVisibility(View.GONE);
        viewHolder.getMessageReferenceImageThumbnail().setVisibility(View.GONE);
        if (viewHolder.getMessageReferencePreviewCancelButton() != null) {
            viewHolder.getMessageReferencePreviewCancelButton().setVisibility(View.GONE);
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
     * Sets the image for the message reference icon depending on the color of the underlying message bubble.
     * @param nonDefaultMessageBubbleColor specifies if the message bubble has a non-default color
     * @param messageReferenceIcon icon for the message reference
     * @param defaultDrawable drawable that will be used as the message reference icon if the message bubble has the default color
     * @param nonDefaultDrawable drawable that will be used as the message reference icon if the message bubble has not the default color
     */
    public static void setMessageReferenceIcon(boolean nonDefaultMessageBubbleColor, ImageView messageReferenceIcon, Drawable defaultDrawable, Drawable nonDefaultDrawable) {
        if (nonDefaultMessageBubbleColor) {
            messageReferenceIcon.setBackground(nonDefaultDrawable);
        } else {
            messageReferenceIcon.setBackground(defaultDrawable);
        }
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