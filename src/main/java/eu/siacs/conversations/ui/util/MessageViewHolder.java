package eu.siacs.conversations.ui.util;

import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import eu.siacs.conversations.ui.widget.CopyTextView;

public class MessageViewHolder {
    private Button loadMoreMessages;
    private ImageView editIndicator;
    private RelativeLayout audioPlayer;
    private LinearLayout messageBox;
    private Button downloadButton;
    private ImageView image;
    private ImageView indicator;
    private ImageView indicatorReceived;
    private TextView time;
    private CopyTextView messageBody;
    private LinearLayout messageReferenceContainer;
    private View messageReferenceBar;
    private TextView messageReferenceInfo;
    private TextView messageReferenceText;
    private ImageView messageReferenceIcon;
    private ImageView messageReferenceImageThumbnail;
    private ImageView contactPicture;
    private TextView statusMessage;
    private TextView encryption;

    public Button getLoadMoreMessages() {
        return loadMoreMessages;
    }

    public void setLoadMoreMessages(Button loadMoreMessages) {
        this.loadMoreMessages = loadMoreMessages;
    }

    public ImageView getEditIndicator() {
        return editIndicator;
    }

    public void setEditIndicator(ImageView editIndicator) {
        this.editIndicator = editIndicator;
    }

    public RelativeLayout getAudioPlayer() {
        return audioPlayer;
    }

    public void setAudioPlayer(RelativeLayout audioPlayer) {
        this.audioPlayer = audioPlayer;
    }

    public LinearLayout getMessageBox() {
        return messageBox;
    }

    public void setMessageBox(LinearLayout messageBox) {
        this.messageBox = messageBox;
    }

    public Button getDownloadButton() {
        return downloadButton;
    }

    public void setDownloadButton(Button downloadButton) {
        this.downloadButton = downloadButton;
    }

    public ImageView getImage() {
        return image;
    }

    public void setImage(ImageView image) {
        this.image = image;
    }

    public ImageView getIndicator() {
        return indicator;
    }

    public void setIndicator(ImageView indicator) {
        this.indicator = indicator;
    }

    public ImageView getIndicatorReceived() {
        return indicatorReceived;
    }

    public void setIndicatorReceived(ImageView indicatorReceived) {
        this.indicatorReceived = indicatorReceived;
    }

    public TextView getTime() {
        return time;
    }

    public void setTime(TextView time) {
        this.time = time;
    }

    public CopyTextView getMessageBody() {
        return messageBody;
    }

    public void setMessageBody(CopyTextView messageBody) {
        this.messageBody = messageBody;
    }

    public LinearLayout getMessageReferenceContainer() {
        return messageReferenceContainer;
    }

    public void setMessageReferenceContainer(LinearLayout messageReferenceContainer) {
        this.messageReferenceContainer = messageReferenceContainer;
    }

    public View getMessageReferenceBar() {
        return messageReferenceBar;
    }

    public void setMessageReferenceBar(View messageReferenceBar) {
        this.messageReferenceBar = messageReferenceBar;
    }

    public TextView getMessageReferenceInfo() {
        return messageReferenceInfo;
    }

    public void setMessageReferenceInfo(TextView messageReferenceInfo) {
        this.messageReferenceInfo = messageReferenceInfo;
    }

    public TextView getMessageReferenceText() {
        return messageReferenceText;
    }

    public void setMessageReferenceText(TextView messageReferenceText) {
        this.messageReferenceText = messageReferenceText;
    }

    public ImageView getMessageReferenceIcon() {
        return messageReferenceIcon;
    }

    public void setMessageReferenceIcon(ImageView messageReferenceIcon) {
        this.messageReferenceIcon = messageReferenceIcon;
    }

    public ImageView getMessageReferenceImageThumbnail() {
        return messageReferenceImageThumbnail;
    }

    public void setMessageReferenceImageThumbnail(ImageView messageReferenceImageThumbnail) {
        this.messageReferenceImageThumbnail = messageReferenceImageThumbnail;
    }

    public ImageView getContactPicture() {
        return contactPicture;
    }

    public void setContactPicture(ImageView contactPicture) {
        this.contactPicture = contactPicture;
    }

    public TextView getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(TextView statusMessage) {
        this.statusMessage = statusMessage;
    }

    public TextView getEncryption() {
        return encryption;
    }

    public void setEncryption(TextView encryption) {
        this.encryption = encryption;
    }
}