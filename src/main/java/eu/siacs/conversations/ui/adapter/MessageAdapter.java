package eu.siacs.conversations.ui.adapter;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.annotation.ColorInt;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.databinding.MessageReceivedBinding;
import eu.siacs.conversations.databinding.MessageReferenceBinding;
import eu.siacs.conversations.databinding.MessageSentBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Message.FileParams;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.http.P1S3UrlStreamHandler;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.MessageArchiveService;
import eu.siacs.conversations.services.NotificationService;
import eu.siacs.conversations.ui.ConversationFragment;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.service.AudioPlayer;
import eu.siacs.conversations.ui.text.DividerSpan;
import eu.siacs.conversations.ui.text.QuoteSpan;
import eu.siacs.conversations.ui.util.MessageReferenceUtils;
import eu.siacs.conversations.ui.util.MyLinkify;
import eu.siacs.conversations.ui.util.ViewUtil;
import eu.siacs.conversations.ui.widget.ClickableMovementMethod;
import eu.siacs.conversations.ui.widget.CopyTextView;
import eu.siacs.conversations.ui.widget.ListSelectionManager;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.EmojiWrapper;
import eu.siacs.conversations.utils.Emoticons;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.StylingHelper;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.mam.MamReference;

public class MessageAdapter extends ArrayAdapter<Message> implements CopyTextView.CopyHandler {

	public static final String DATE_SEPARATOR_BODY = "DATE_SEPARATOR";
	public static final int SENT = 0;
	public static final int RECEIVED = 1;
	private static final int STATUS = 2;
	private static final int DATE_SEPARATOR = 3;
	private final XmppActivity activity;
	private final ListSelectionManager listSelectionManager = new ListSelectionManager();
	private final AudioPlayer audioPlayer;
	private List<String> highlightedTerm = null;
	private DisplayMetrics metrics;
	private OnContactPictureClicked mOnContactPictureClickedListener;
	private OnContactPictureLongClicked mOnContactPictureLongClickedListener;
	private boolean mIndicateReceived = false;
	private boolean mUseGreenBackground = false;
	private OnQuoteListener onQuoteListener;
	public MessageAdapter(XmppActivity activity, List<Message> messages) {
		super(activity, 0, messages);
		this.audioPlayer = new AudioPlayer(this);
		this.activity = activity;
		metrics = getContext().getResources().getDisplayMetrics();
		updatePreferences();
	}

	public static boolean cancelPotentialWork(Message message, ImageView imageView) {
		final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

		if (bitmapWorkerTask != null) {
			final Message oldMessage = bitmapWorkerTask.message;
			if (oldMessage == null || message != oldMessage) {
				bitmapWorkerTask.cancel(true);
			} else {
				return false;
			}
		}
		return true;
	}

	private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
		if (imageView != null) {
			final Drawable drawable = imageView.getDrawable();
			if (drawable instanceof AsyncDrawable) {
				final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
				return asyncDrawable.getBitmapWorkerTask();
			}
		}
		return null;
	}

	private static void resetClickListener(View... views) {
		for (View view : views) {
			view.setOnClickListener(null);
		}
	}

	public void flagScreenOn() {
		activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	public void flagScreenOff() {
		activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	public void setOnContactPictureClicked(OnContactPictureClicked listener) {
		this.mOnContactPictureClickedListener = listener;
	}

	public Activity getActivity() {
		return activity;
	}

	public void setOnContactPictureLongClicked(
			OnContactPictureLongClicked listener) {
		this.mOnContactPictureLongClickedListener = listener;
	}

	public void setOnQuoteListener(OnQuoteListener listener) {
		this.onQuoteListener = listener;
	}

	@Override
	public int getViewTypeCount() {
		return 4;
	}

	private int getItemViewType(Message message) {
		if (message.getType() == Message.TYPE_STATUS) {
			if (DATE_SEPARATOR_BODY.equals(message.getBody())) {
				return DATE_SEPARATOR;
			} else {
				return STATUS;
			}
		} else if (message.getStatus() <= Message.STATUS_RECEIVED) {
			return RECEIVED;
		}

		return SENT;
	}

	@Override
	public int getItemViewType(int position) {
		return this.getItemViewType(getItem(position));
	}

	private int getMessageTextColor(boolean onDark, boolean primary) {
		if (onDark) {
			return ContextCompat.getColor(activity, primary ? R.color.white : R.color.white70);
		} else {
			return ContextCompat.getColor(activity, primary ? R.color.black87 : R.color.black54);
		}
	}

	private void displayStatus(ViewHolder viewHolder, Message message, int type, boolean darkBackground) {
		String filesize = null;
		String info = null;
		boolean error = false;
		if (viewHolder.indicatorReceived != null) {
			viewHolder.indicatorReceived.setVisibility(View.GONE);
		}

		if (viewHolder.editIndicator != null) {
			if (message.edited()) {
				viewHolder.editIndicator.setVisibility(View.VISIBLE);
				viewHolder.editIndicator.setImageResource(darkBackground ? R.drawable.ic_mode_edit_white_18dp : R.drawable.ic_mode_edit_black_18dp);
				viewHolder.editIndicator.setAlpha(darkBackground ? 0.7f : 0.57f);
			} else {
				viewHolder.editIndicator.setVisibility(View.GONE);
			}
		}
		boolean multiReceived = message.getConversation().getMode() == Conversation.MODE_MULTI
				&& message.getMergedStatus() <= Message.STATUS_RECEIVED;
		if (message.getType() == Message.TYPE_IMAGE || message.getType() == Message.TYPE_FILE || message.getTransferable() != null) {
			FileParams params = message.getFileParams();
			if (params.size > (1.5 * 1024 * 1024)) {
				filesize = Math.round(params.size * 1f / (1024 * 1024)) + " MiB";
			} else if (params.size >= 1024) {
				filesize = Math.round(params.size * 1f / 1024) + " KiB";
			} else if (params.size > 0) {
				filesize = params.size + " B";
			}
			if (message.getTransferable() != null && message.getTransferable().getStatus() == Transferable.STATUS_FAILED) {
				error = true;
			}
		}
		switch (message.getMergedStatus()) {
			case Message.STATUS_WAITING:
				info = getContext().getString(R.string.waiting);
				break;
			case Message.STATUS_UNSENT:
				Transferable d = message.getTransferable();
				if (d != null) {
					info = getContext().getString(R.string.sending_file, d.getProgress());
				} else {
					info = getContext().getString(R.string.sending);
				}
				break;
			case Message.STATUS_OFFERED:
				info = getContext().getString(R.string.offering);
				break;
			case Message.STATUS_SEND_RECEIVED:
				if (mIndicateReceived) {
					viewHolder.indicatorReceived.setVisibility(View.VISIBLE);
				}
				break;
			case Message.STATUS_SEND_DISPLAYED:
				if (mIndicateReceived) {
					viewHolder.indicatorReceived.setVisibility(View.VISIBLE);
				}
				break;
			case Message.STATUS_SEND_FAILED:
				if (Message.ERROR_MESSAGE_CANCELLED.equals(message.getErrorMessage())) {
					info = getContext().getString(R.string.cancelled);
				} else {
					info = getContext().getString(R.string.send_failed);
				}
				error = true;
				break;
			default:
				if (multiReceived) {
					info = UIHelper.getMessageDisplayName(message);
				}
				break;
		}
		if (error && type == SENT) {
			viewHolder.time.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Caption_Warning);
		} else {
			if (darkBackground) {
				viewHolder.time.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Caption_OnDark);
			} else {
				viewHolder.time.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Caption);
			}
			viewHolder.time.setTextColor(this.getMessageTextColor(darkBackground, false));
		}
		if (message.getEncryption() == Message.ENCRYPTION_NONE) {
			viewHolder.indicator.setVisibility(View.GONE);
		} else {
			boolean verified = false;
			if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
				final FingerprintStatus status = message.getConversation()
						.getAccount().getAxolotlService().getFingerprintTrust(
								message.getFingerprint());
				if (status != null && status.isVerified()) {
					verified = true;
				}
			}
			if (verified) {
				viewHolder.indicator.setImageResource(darkBackground ? R.drawable.ic_verified_user_white_18dp : R.drawable.ic_verified_user_black_18dp);
			} else {
				viewHolder.indicator.setImageResource(darkBackground ? R.drawable.ic_lock_white_18dp : R.drawable.ic_lock_black_18dp);
			}
			if (darkBackground) {
				viewHolder.indicator.setAlpha(0.7f);
			} else {
				viewHolder.indicator.setAlpha(0.57f);
			}
			viewHolder.indicator.setVisibility(View.VISIBLE);
		}

		String formattedTime = UIHelper.readableTimeDifferenceFull(getContext(), message.getMergedTimeSent());
		if (message.getStatus() <= Message.STATUS_RECEIVED) {
			if ((filesize != null) && (info != null)) {
				viewHolder.time.setText(formattedTime + " \u00B7 " + filesize + " \u00B7 " + info);
			} else if ((filesize == null) && (info != null)) {
				viewHolder.time.setText(formattedTime + " \u00B7 " + info);
			} else if ((filesize != null) && (info == null)) {
				viewHolder.time.setText(formattedTime + " \u00B7 " + filesize);
			} else {
				viewHolder.time.setText(formattedTime);
			}
		} else {
			if ((filesize != null) && (info != null)) {
				viewHolder.time.setText(filesize + " \u00B7 " + info);
			} else if ((filesize == null) && (info != null)) {
				if (error) {
					viewHolder.time.setText(info + " \u00B7 " + formattedTime);
				} else {
					viewHolder.time.setText(info);
				}
			} else if ((filesize != null) && (info == null)) {
				viewHolder.time.setText(filesize + " \u00B7 " + formattedTime);
			} else {
				viewHolder.time.setText(formattedTime);
			}
		}
	}

	private void displayInfoMessage(ViewHolder viewHolder, CharSequence text, boolean darkBackground) {
		viewHolder.downloadButton.setVisibility(View.GONE);
		viewHolder.audioPlayer.setVisibility(View.GONE);
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.VISIBLE);
		viewHolder.messageBody.setText(text);
		if (darkBackground) {
			viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1_Secondary_OnDark);
		} else {
			viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1_Secondary);
		}
		viewHolder.messageBody.setTextIsSelectable(false);
	}

	private void displayEmojiMessage(final ViewHolder viewHolder, final String body, final boolean darkBackground) {
		viewHolder.downloadButton.setVisibility(View.GONE);
		viewHolder.audioPlayer.setVisibility(View.GONE);
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.VISIBLE);
		if (darkBackground) {
			viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1_Emoji_OnDark);
		} else {
			viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1_Emoji);
		}
		Spannable span = new SpannableString(body);
		float size = Emoticons.isEmoji(body) ? 3.0f : 2.0f;
		span.setSpan(new RelativeSizeSpan(size), 0, body.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		viewHolder.messageBody.setText(EmojiWrapper.transform(span));
	}

	private void applyQuoteSpan(SpannableStringBuilder body, int start, int end, boolean darkBackground) {
		if (start > 1 && !"\n\n".equals(body.subSequence(start - 2, start).toString())) {
			body.insert(start++, "\n");
			body.setSpan(new DividerSpan(false), start - 2, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			end++;
		}
		if (end < body.length() - 1 && !"\n\n".equals(body.subSequence(end, end + 2).toString())) {
			body.insert(end, "\n");
			body.setSpan(new DividerSpan(false), end, end + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
		int color = darkBackground ? this.getMessageTextColor(darkBackground, false)
				: ContextCompat.getColor(activity, R.color.green700_desaturated);
		DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
		body.setSpan(new QuoteSpan(color, metrics), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	}

	/**
	 * Applies QuoteSpan to group of lines which starts with > or » characters.
	 * Appends line breaks and applies DividerSpan to them to show a padding between quote and text.
	 */
	private boolean handleTextQuotes(SpannableStringBuilder body, boolean darkBackground) {
		boolean startsWithQuote = false;
		char previous = '\n';
		int lineStart = -1;
		int lineTextStart = -1;
		int quoteStart = -1;
		for (int i = 0; i <= body.length(); i++) {
			char current = body.length() > i ? body.charAt(i) : '\n';
			if (lineStart == -1) {
				if (previous == '\n') {
					if ((current == '>' && UIHelper.isPositionFollowedByQuoteableCharacter(body, i))
							|| current == '\u00bb' && !UIHelper.isPositionFollowedByQuote(body, i)) {
						// Line start with quote
						lineStart = i;
						if (quoteStart == -1) quoteStart = i;
						if (i == 0) startsWithQuote = true;
					} else if (quoteStart >= 0) {
						// Line start without quote, apply spans there
						applyQuoteSpan(body, quoteStart, i - 1, darkBackground);
						quoteStart = -1;
					}
				}
			} else {
				// Remove extra spaces between > and first character in the line
				// > character will be removed too
				if (current != ' ' && lineTextStart == -1) {
					lineTextStart = i;
				}
				if (current == '\n') {
					body.delete(lineStart, lineTextStart);
					i -= lineTextStart - lineStart;
					if (i == lineStart) {
						// Avoid empty lines because span over empty line can be hidden
						body.insert(i++, " ");
					}
					lineStart = -1;
					lineTextStart = -1;
				}
			}
			previous = current;
		}
		if (quoteStart >= 0) {
			// Apply spans to finishing open quote
			applyQuoteSpan(body, quoteStart, body.length(), darkBackground);
		}
		return startsWithQuote;
	}

	private void displayTextMessage(final ViewHolder viewHolder, final Message message, boolean darkBackground, int type) {
		constructTextMessage(viewHolder, message, darkBackground, type);
	}

	/**
	 * Displays the text, image, preview image or tag of a referenced message next to a bar that indicates the referencing
	 * and underneath the comment on that message.
	 */
	private void displayReferencingMessage(final ViewHolder viewHolder, final Message message, final Message referencedMessage, boolean darkBackground, int type) {
		// Show the message reference area.
		MessageReferenceUtils.displayMessageReference(activity, getPosition(referencedMessage), viewHolder.messageReferenceBinding, message, referencedMessage, darkBackground);

		// Show the comment on the referenced message.
		constructTextMessage(viewHolder, message, darkBackground, type);
	}

	private void displayDownloadableMessage(ViewHolder viewHolder, final Message message, String text) {
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.GONE);
		viewHolder.audioPlayer.setVisibility(View.GONE);
		viewHolder.downloadButton.setVisibility(View.VISIBLE);
		viewHolder.downloadButton.setText(text);
		viewHolder.downloadButton.setOnClickListener(v -> ConversationFragment.downloadFile(activity, message));
	}

	private void displayOpenableMessage(ViewHolder viewHolder, final Message message) {
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.GONE);
		viewHolder.audioPlayer.setVisibility(View.GONE);
		viewHolder.downloadButton.setVisibility(View.VISIBLE);
		viewHolder.downloadButton.setText(activity.getString(R.string.open_x_file, UIHelper.getFileDescriptionString(activity, message)));
		viewHolder.downloadButton.setOnClickListener(v -> openDownloadable(message));
	}

	private void displayLocationMessage(ViewHolder viewHolder, final Message message) {
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.GONE);
		viewHolder.audioPlayer.setVisibility(View.GONE);
		viewHolder.downloadButton.setVisibility(View.VISIBLE);
		viewHolder.downloadButton.setText(R.string.show_location);
		viewHolder.downloadButton.setOnClickListener(v -> showLocation(message));
	}

	private void displayAudioMessage(ViewHolder viewHolder, Message message, boolean darkBackground) {
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.GONE);
		viewHolder.downloadButton.setVisibility(View.GONE);
		final RelativeLayout audioPlayer = viewHolder.audioPlayer;
		audioPlayer.setVisibility(View.VISIBLE);
		AudioPlayer.ViewHolder.get(audioPlayer).setDarkBackground(darkBackground);
		this.audioPlayer.init(audioPlayer, message);
	}

	private void displayImageMessage(ViewHolder viewHolder, final Message message) {
		viewHolder.downloadButton.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.GONE);
		viewHolder.audioPlayer.setVisibility(View.GONE);
		viewHolder.image.setVisibility(View.VISIBLE);
		FileParams params = message.getFileParams();
		double target = metrics.density * activity.BITMAP_SCALE;
		int scaledW;
		int scaledH;
		if (Math.max(params.height, params.width) * metrics.density <= target) {
			scaledW = (int) (params.width * metrics.density);
			scaledH = (int) (params.height * metrics.density);
		} else if (Math.max(params.height, params.width) <= target) {
			scaledW = params.width;
			scaledH = params.height;
		} else if (params.width <= params.height) {
			scaledW = (int) (params.width / ((double) params.height / target));
			scaledH = (int) target;
		} else {
			scaledW = (int) target;
			scaledH = (int) (params.height / ((double) params.width / target));
		}
		LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(scaledW, scaledH);
		layoutParams.setMargins(0, (int) (metrics.density * 4), 0, (int) (metrics.density * 4));
		viewHolder.image.setLayoutParams(layoutParams);
		activity.loadBitmap(message, viewHolder.image);
		viewHolder.image.setOnClickListener(v -> openDownloadable(message));
	}

    /**
     * Helps to display a text-only message or a hybrid one
     * (e.g., a reference to an image message with a text comment).
     *
     * Only to be used in conjunction with a display*Message() method.
     * For instance see {@link #displayReferencingMessage} or {@link #displayTextMessage}
     */
    private void constructTextMessage(final ViewHolder viewHolder, final Message message, boolean darkBackground, int type) {
        viewHolder.downloadButton.setVisibility(View.GONE);
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.audioPlayer.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.VISIBLE);

        if (darkBackground) {
            viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1_OnDark);
        } else {
            viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1);
        }
        viewHolder.messageBody.setHighlightColor(ContextCompat.getColor(activity, darkBackground
                ? (type == SENT || !mUseGreenBackground ? R.color.black26 : R.color.grey800) : R.color.grey500));
        viewHolder.messageBody.setTypeface(null, Typeface.NORMAL);

        if (message.getBody() != null) {
            final String nick = UIHelper.getMessageDisplayName(message);
            SpannableStringBuilder body = message.getMergedBody();
            boolean hasMeCommand = message.hasMeCommand();
            if (hasMeCommand) {
                body = body.replace(0, Message.ME_COMMAND.length(), nick + " ");
            }
            if (body.length() > Config.MAX_DISPLAY_MESSAGE_CHARS) {
                body = new SpannableStringBuilder(body, 0, Config.MAX_DISPLAY_MESSAGE_CHARS);
                body.append("\u2026");
            }
            Message.MergeSeparator[] mergeSeparators = body.getSpans(0, body.length(), Message.MergeSeparator.class);
            for (Message.MergeSeparator mergeSeparator : mergeSeparators) {
                int start = body.getSpanStart(mergeSeparator);
                int end = body.getSpanEnd(mergeSeparator);
                body.setSpan(new DividerSpan(true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            boolean startsWithQuote = handleTextQuotes(body, darkBackground);
            if (message.getType() != Message.TYPE_PRIVATE) {
                if (hasMeCommand) {
                    body.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 0, nick.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else {
                String privateMarker;
                if (message.getStatus() <= Message.STATUS_RECEIVED) {
                    privateMarker = activity.getString(R.string.private_message);
                } else {
                    final String to;
                    if (message.getCounterpart() != null) {
                        to = message.getCounterpart().getResource();
                    } else {
                        to = "";
                    }
                    privateMarker = activity.getString(R.string.private_message_to, to);
                }
                body.insert(0, privateMarker);
                int privateMarkerIndex = privateMarker.length();
                if (startsWithQuote) {
                    body.insert(privateMarkerIndex, "\n\n");
                    body.setSpan(new DividerSpan(false), privateMarkerIndex, privateMarkerIndex + 2,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    body.insert(privateMarkerIndex, " ");
                }
                body.setSpan(new ForegroundColorSpan(getMessageTextColor(darkBackground, false)), 0, privateMarkerIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                body.setSpan(new StyleSpan(Typeface.BOLD), 0, privateMarkerIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (hasMeCommand) {
                    body.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), privateMarkerIndex + 1,
                            privateMarkerIndex + 1 + nick.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            if (message.getConversation().getMode() == Conversation.MODE_MULTI && message.getStatus() == Message.STATUS_RECEIVED) {
                if (message.getConversation() instanceof Conversation) {
                    final Conversation conversation = (Conversation) message.getConversation();
                    Pattern pattern = NotificationService.generateNickHighlightPattern(conversation.getMucOptions().getActualNick());
                    Matcher matcher = pattern.matcher(body);
                    while (matcher.find()) {
                        body.setSpan(new StyleSpan(Typeface.BOLD), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
            Matcher matcher = Emoticons.getEmojiPattern(body).matcher(body);
            while (matcher.find()) {
                if (matcher.start() < matcher.end()) {
                    body.setSpan(new RelativeSizeSpan(1.2f), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }

            StylingHelper.format(body, viewHolder.messageBody.getCurrentTextColor());
            if (highlightedTerm != null) {
                StylingHelper.highlight(activity, body, highlightedTerm, StylingHelper.isDarkText(viewHolder.messageBody));
            }
            MyLinkify.addLinks(body,true);
            viewHolder.messageBody.setAutoLinkMask(0);
            viewHolder.messageBody.setText(EmojiWrapper.transform(body));
            viewHolder.messageBody.setTextIsSelectable(true);
            viewHolder.messageBody.setMovementMethod(ClickableMovementMethod.getInstance());
            listSelectionManager.onUpdate(viewHolder.messageBody, message);
        } else {
            viewHolder.messageBody.setText("");
            viewHolder.messageBody.setTextIsSelectable(false);
        }
    }

	private void loadMoreMessages(Conversation conversation) {
		conversation.setLastClearHistory(0, null);
		activity.xmppConnectionService.updateConversation(conversation);
		conversation.setHasMessagesLeftOnServer(true);
		conversation.setFirstMamReference(null);
		long timestamp = conversation.getLastMessageTransmitted().getTimestamp();
		if (timestamp == 0) {
			timestamp = System.currentTimeMillis();
		}
		conversation.messagesLoaded.set(true);
		MessageArchiveService.Query query = activity.xmppConnectionService.getMessageArchiveService().query(conversation, new MamReference(0), timestamp, false);
		if (query != null) {
			Toast.makeText(activity, R.string.fetching_history_from_server, Toast.LENGTH_LONG).show();
		} else {
			Toast.makeText(activity, R.string.not_fetching_history_retention_period, Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		final Message message = getItem(position);
		final boolean omemoEncryption = message.getEncryption() == Message.ENCRYPTION_AXOLOTL;
		final boolean isInValidSession = message.isValidInSession() && (!omemoEncryption || message.isTrusted());
		final Conversational conversation = message.getConversation();
		final Account account = conversation.getAccount();
		final int type = getItemViewType(position);
		ViewHolder viewHolder;

		if (view == null) {
			viewHolder = new ViewHolder();
			switch (type) {
				case DATE_SEPARATOR:
					view = activity.getLayoutInflater().inflate(R.layout.message_date_bubble, parent, false);
					viewHolder.statusMessage = (TextView) view.findViewById(R.id.message_body);
					viewHolder.messageBox = (LinearLayout) view.findViewById(R.id.message_box);
					break;
				case SENT:
					MessageSentBinding messageSentBinding = MessageSentBinding.inflate(activity.getLayoutInflater());
					view = messageSentBinding.getRoot();

					viewHolder.messageBox = messageSentBinding.messageBox;
					viewHolder.messageReferenceBinding = messageSentBinding.messageContent.messageReference;
					viewHolder.contactPicture = messageSentBinding.messagePhoto;
					viewHolder.downloadButton = messageSentBinding.messageContent.downloadButton;
					viewHolder.indicator = messageSentBinding.securityIndicator;
					viewHolder.editIndicator = messageSentBinding.editIndicator;
					viewHolder.image = messageSentBinding.messageContent.messageImage;
					viewHolder.messageBody = messageSentBinding.messageContent.messageBody;
					viewHolder.time = messageSentBinding.messageTime;
					viewHolder.audioPlayer = messageSentBinding.messageContent.audioPlayer;

                    viewHolder.indicatorReceived = messageSentBinding.indicatorReceived;
					break;
				case RECEIVED:
					MessageReceivedBinding messageReceivedBinding = MessageReceivedBinding.inflate(activity.getLayoutInflater());
					view = messageReceivedBinding.getRoot();
                    viewHolder.messageBox = messageReceivedBinding.messageBox;
					viewHolder.messageReferenceBinding = messageReceivedBinding.messageContent.messageReference;
					viewHolder.contactPicture = messageReceivedBinding.messagePhoto;
                    viewHolder.downloadButton = messageReceivedBinding.messageContent.downloadButton;
                    viewHolder.indicator = messageReceivedBinding.securityIndicator;
                    viewHolder.editIndicator = messageReceivedBinding.editIndicator;
                    viewHolder.image = messageReceivedBinding.messageContent.messageImage;
                    viewHolder.messageBody = messageReceivedBinding.messageContent.messageBody;
                    viewHolder.time = messageReceivedBinding.messageTime;
                    viewHolder.audioPlayer = messageReceivedBinding.messageContent.audioPlayer;

					viewHolder.encryption = messageReceivedBinding.messageEncryption;
					break;
				case STATUS:
					view = activity.getLayoutInflater().inflate(R.layout.message_status, parent, false);
					viewHolder.contactPicture = (ImageView) view.findViewById(R.id.message_photo);
					viewHolder.statusMessage = (TextView) view.findViewById(R.id.status_message);
					viewHolder.loadMoreMessages = (Button) view.findViewById(R.id.load_more_messages);
					break;
				default:
					throw new AssertionError("Unknown view type");
			}
			if (viewHolder.messageBody != null) {
				listSelectionManager.onCreate(viewHolder.messageBody,
						new MessageBodyActionModeCallback(viewHolder.messageBody));
				viewHolder.messageBody.setCopyHandler(this);
			}
			view.setTag(viewHolder);
		} else {
			viewHolder = (ViewHolder) view.getTag();
			if (viewHolder == null) {
				return view;
			}
		}

		boolean darkBackground = type == RECEIVED && (!isInValidSession || mUseGreenBackground) || activity.isDarkTheme();

		if (type == DATE_SEPARATOR) {
			if (UIHelper.today(message.getTimeSent())) {
				viewHolder.statusMessage.setText(R.string.today);
			} else if (UIHelper.yesterday(message.getTimeSent())) {
				viewHolder.statusMessage.setText(R.string.yesterday);
			} else {
				viewHolder.statusMessage.setText(DateUtils.formatDateTime(activity, message.getTimeSent(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR));
			}
			viewHolder.messageBox.setBackgroundResource(activity.isDarkTheme() ? R.drawable.date_bubble_grey : R.drawable.date_bubble_white);
			return view;
		} else if (type == STATUS) {
			if ("LOAD_MORE".equals(message.getBody())) {
				viewHolder.statusMessage.setVisibility(View.GONE);
				viewHolder.contactPicture.setVisibility(View.GONE);
				viewHolder.loadMoreMessages.setVisibility(View.VISIBLE);
				viewHolder.loadMoreMessages.setOnClickListener(v -> loadMoreMessages((Conversation) message.getConversation()));
			} else {
				viewHolder.statusMessage.setVisibility(View.VISIBLE);
				viewHolder.loadMoreMessages.setVisibility(View.GONE);
				viewHolder.statusMessage.setText(message.getBody());
				boolean showAvatar;
				if (conversation.getMode() == Conversation.MODE_SINGLE) {
					showAvatar = true;
					loadAvatar(message, viewHolder.contactPicture, activity.getPixel(32));
				} else if (message.getCounterpart() != null || message.getTrueCounterpart() != null || (message.getCounterparts() != null && message.getCounterparts().size() > 0)) {
					showAvatar = true;
					loadAvatar(message, viewHolder.contactPicture, activity.getPixel(32));
				} else {
					showAvatar = false;
				}
				if (showAvatar) {
					viewHolder.contactPicture.setAlpha(0.5f);
					viewHolder.contactPicture.setVisibility(View.VISIBLE);
				} else {
					viewHolder.contactPicture.setVisibility(View.GONE);
				}
			}
			return view;
		} else {
			loadAvatar(message, viewHolder.contactPicture, activity.getPixel(48));
		}

		resetClickListener(viewHolder.messageBox, viewHolder.messageBody);

		viewHolder.contactPicture.setOnClickListener(v -> {
			if (MessageAdapter.this.mOnContactPictureClickedListener != null) {
				MessageAdapter.this.mOnContactPictureClickedListener
						.onContactPictureClicked(message);
			}

		});
		viewHolder.contactPicture.setOnLongClickListener(v -> {
			if (MessageAdapter.this.mOnContactPictureLongClickedListener != null) {
				MessageAdapter.this.mOnContactPictureLongClickedListener
						.onContactPictureLongClicked(v, message);
				return true;
			} else {
				return false;
			}
		});

		// Hide all referencing message views to make them individually visible later.
		MessageReferenceUtils.hideMessageReference(viewHolder.messageReferenceBinding);

		final Transferable transferable = message.getTransferable();
		if (transferable != null && transferable.getStatus() != Transferable.STATUS_UPLOADING) {
			if (transferable.getStatus() == Transferable.STATUS_OFFER) {
				displayDownloadableMessage(viewHolder, message, activity.getString(R.string.download_x_file, UIHelper.getFileDescriptionString(activity, message)));
			} else if (transferable.getStatus() == Transferable.STATUS_OFFER_CHECK_FILESIZE) {
				displayDownloadableMessage(viewHolder, message, activity.getString(R.string.check_x_filesize, UIHelper.getFileDescriptionString(activity, message)));
			} else {
				displayInfoMessage(viewHolder, UIHelper.getMessagePreview(activity, message).first, darkBackground);
			}

		// Display a referenced message and a comment for it if the referencing message has a message reference that matches a locally available message-
		// Otherwise display the referencing message normally.
		} else if (message.hasMessageReference() && message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED && message.getEncryption() != Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE) {
			Message referencedMessage = ((Conversation) conversation).findMessageWithUuidOrRemoteMsgId(message.getMessageReference());

			// Try to load the referenced message from the DB if it is null and could not be found in the currently loaded conversation.
			// If it cannot be loaded from the DB it will remain null.
			if(referencedMessage == null){
				referencedMessage = activity.xmppConnectionService.databaseBackend.getMsgByUuidOrRemoteMsgId((Conversation) conversation, message.getMessageReference());
			}

			// Use the referenced message if a message was found for the given reference.
			// This is useful if the sending client used an ID that cannot be found locally.
			if (referencedMessage != null && referencedMessage.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED && referencedMessage.getEncryption() != Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE) {
				String messageBody = message.getBody();
				String[] messageBodyLines = messageBody.split("\n");
				int numberOfMessageBodyLines = messageBodyLines.length;
				String[] referencedMessageBodyLines = referencedMessage.getBody().split("\n");
				int numberOfReferencedMessageBodyLines = referencedMessageBodyLines.length;

				// Delete legacy quotation added for backward compatibility if present but preserve independent quotations.
				if (messageBody.length() > 0 && numberOfMessageBodyLines >= numberOfReferencedMessageBodyLines && (messageBody.charAt(0) == '>' || messageBody.charAt(0) == '\u00bb')) {

					// If the referenced message is a file message
					// and the first quoted line is the URL of the referenced file message,
					// remove that line from the message's body.
					// This is necessary as a separate case for image messages since the URL can be compared without other FileParams like the dimensions.
					// If the referenced message is not a file message, remove all quoted lines from the message's body
					// that are lines of the referenced message.
					if (referencedMessage.hasFileOnRemoteHost()) {
						String line = messageBodyLines[0];
						if (UIHelper.isQuotationLine(line)) {
							if (line.substring(1).trim().equals(referencedMessage.getFileParams().url.toString())) {
								message.setBody(MessageReferenceUtils.createStringWithLinesOutOfStringArray(messageBodyLines, 1, messageBodyLines.length));
							}
						}
					} else {
						// Take only the part of the body that contains the comment without legacy quotation.
						int currentLine = 0;
						boolean quotationEqualsReferencedMessage = true;
						for (String line : messageBodyLines) {
							if (currentLine < numberOfReferencedMessageBodyLines) {
								if (!(UIHelper.isQuotationLine(line) && line.substring(1).trim().equals(referencedMessageBodyLines[currentLine].trim()))) {
									quotationEqualsReferencedMessage = false;
									break;
								}
							} else {
								break;
							}
							currentLine++;
						}
						if (quotationEqualsReferencedMessage) {
							message.setBody(MessageReferenceUtils.createStringWithLinesOutOfStringArray(messageBodyLines, currentLine, messageBodyLines.length));
							activity.xmppConnectionService.updateMessage(message);
						}
					}
				}
			}

			// Display a referenced message of any type.
			// or only an info message for a message reference that has no associated message.
			displayReferencingMessage(viewHolder, message, referencedMessage, darkBackground, type);

		} else if (message.isFileOrImage() && message.getEncryption() != Message.ENCRYPTION_PGP && message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED) {
			if (message.isImageOrVideo()) {
				displayImageMessage(viewHolder, message);
			} else if (message.isAudio()) {
				displayAudioMessage(viewHolder, message, darkBackground);
			} else {
				displayOpenableMessage(viewHolder, message);
			}
		} else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
			if (account.isPgpDecryptionServiceConnected()) {
				if (conversation instanceof Conversation && !account.hasPendingPgpIntent((Conversation) conversation)) {
					displayInfoMessage(viewHolder, activity.getString(R.string.message_decrypting), darkBackground);
				} else {
					displayInfoMessage(viewHolder, activity.getString(R.string.pgp_message), darkBackground);
				}
			} else {
				displayInfoMessage(viewHolder, activity.getString(R.string.install_openkeychain), darkBackground);
				viewHolder.messageBox.setOnClickListener(this::promptOpenKeychainInstall);
				viewHolder.messageBody.setOnClickListener(this::promptOpenKeychainInstall);
			}
		} else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
			displayInfoMessage(viewHolder, activity.getString(R.string.decryption_failed), darkBackground);
		} else if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE) {
			displayInfoMessage(viewHolder, activity.getString(R.string.not_encrypted_for_this_device), darkBackground);
		} else {
			if (message.isGeoUri()) {
				displayLocationMessage(viewHolder, message);
			} else if (message.bodyIsOnlyEmojis() && message.getType() != Message.TYPE_PRIVATE) {
				displayEmojiMessage(viewHolder, message.getBody().trim(), darkBackground);
			} else if (message.treatAsDownloadable()) {
				try {
					URL url = new URL(message.getBody());
					if (P1S3UrlStreamHandler.PROTOCOL_NAME.equalsIgnoreCase(url.getProtocol())) {
						displayDownloadableMessage(viewHolder,
								message,
								activity.getString(R.string.check_x_filesize,
										UIHelper.getFileDescriptionString(activity, message)));
					} else {
						displayDownloadableMessage(viewHolder,
								message,
								activity.getString(R.string.check_x_filesize_on_host,
										UIHelper.getFileDescriptionString(activity, message),
										url.getHost()));
					}
				} catch (Exception e) {
					displayDownloadableMessage(viewHolder,
							message,
							activity.getString(R.string.check_x_filesize,
									UIHelper.getFileDescriptionString(activity, message)));
				}
			} else {
				displayTextMessage(viewHolder, message, darkBackground, type);
			}
		}

		if (type == RECEIVED) {
			if (isInValidSession) {
				int bubble;
				if (!mUseGreenBackground) {
					bubble = activity.getThemeResource(R.attr.message_bubble_received_monochrome, R.drawable.message_bubble_received_white);
				} else {
					bubble = activity.getThemeResource(R.attr.message_bubble_received_green, R.drawable.message_bubble_received);
				}
				viewHolder.messageBox.setBackgroundResource(bubble);
				viewHolder.encryption.setVisibility(View.GONE);
			} else {
				viewHolder.messageBox.setBackgroundResource(R.drawable.message_bubble_received_warning);
				viewHolder.encryption.setVisibility(View.VISIBLE);
				if (omemoEncryption && !message.isTrusted()) {
					viewHolder.encryption.setText(R.string.not_trusted);
				} else {
					viewHolder.encryption.setText(CryptoHelper.encryptionTypeToText(message.getEncryption()));
				}
			}
		}

		displayStatus(viewHolder, message, type, darkBackground);

		return view;
	}

	private void promptOpenKeychainInstall(View view) {
		activity.showInstallPgpDialog();
	}

	@Override
	public void notifyDataSetChanged() {
		listSelectionManager.onBeforeNotifyDataSetChanged();
		super.notifyDataSetChanged();
		listSelectionManager.onAfterNotifyDataSetChanged();
	}

	private String transformText(CharSequence text, int start, int end, boolean forCopy) {
		SpannableStringBuilder builder = new SpannableStringBuilder(text);
		Object copySpan = new Object();
		builder.setSpan(copySpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		DividerSpan[] dividerSpans = builder.getSpans(0, builder.length(), DividerSpan.class);
		for (DividerSpan dividerSpan : dividerSpans) {
			builder.replace(builder.getSpanStart(dividerSpan), builder.getSpanEnd(dividerSpan),
					dividerSpan.isLarge() ? "\n\n" : "\n");
		}
		start = builder.getSpanStart(copySpan);
		end = builder.getSpanEnd(copySpan);
		if (start == -1 || end == -1) return "";
		builder = new SpannableStringBuilder(builder, start, end);
		if (forCopy) {
			QuoteSpan[] quoteSpans = builder.getSpans(0, builder.length(), QuoteSpan.class);
			for (QuoteSpan quoteSpan : quoteSpans) {
				builder.insert(builder.getSpanStart(quoteSpan), "> ");
			}
		}
		return builder.toString();
	}

	@Override
	public String transformTextForCopy(CharSequence text, int start, int end) {
		if (text instanceof Spanned) {
			return transformText(text, start, end, true);
		} else {
			return text.toString().substring(start, end);
		}
	}

	public FileBackend getFileBackend() {
		return activity.xmppConnectionService.getFileBackend();
	}

	public void stopAudioPlayer() {
		audioPlayer.stop();
	}

	public void unregisterListenerInAudioPlayer() {
		audioPlayer.unregisterListener();
	}

	public void startStopPending() {
		audioPlayer.startStopPending();
	}

	public void openDownloadable(Message message) {
		if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			ConversationFragment.registerPendingMessage(activity, message);
			ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, ConversationsActivity.REQUEST_OPEN_MESSAGE);
			return;
		}
		DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
		if (!file.exists()) {
			Toast.makeText(activity, R.string.file_deleted, Toast.LENGTH_SHORT).show();
			return;
		}
		String mime = file.getMimeType();
		if (mime == null) {
			mime = "*/*";
		}
		ViewUtil.view(activity, file, mime);
	}

	public void showLocation(Message message) {
		for (Intent intent : GeoHelper.createGeoIntentsFromMessage(activity, message)) {
			if (intent.resolveActivity(getContext().getPackageManager()) != null) {
				getContext().startActivity(intent);
				return;
			}
		}
		Toast.makeText(activity, R.string.no_application_found_to_display_location, Toast.LENGTH_SHORT).show();
	}

	public void updatePreferences() {
		SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(activity);
		this.mIndicateReceived = p.getBoolean("indicate_received", activity.getResources().getBoolean(R.bool.indicate_received));
		this.mUseGreenBackground = p.getBoolean("use_green_background", activity.getResources().getBoolean(R.bool.use_green_background));
	}

	public void loadAvatar(Message message, ImageView imageView, int size) {
		if (cancelPotentialWork(message, imageView)) {
			final Bitmap bm = activity.avatarService().get(message, size, true);
			if (bm != null) {
				cancelPotentialWork(message, imageView);
				imageView.setImageBitmap(bm);
				imageView.setBackgroundColor(Color.TRANSPARENT);
			} else {
				@ColorInt int bg;
				if (message.getType() == Message.TYPE_STATUS && message.getCounterparts() != null && message.getCounterparts().size() > 1) {
					bg = Color.TRANSPARENT;
				} else {
					bg = UIHelper.getColorForName(UIHelper.getMessageDisplayName(message));
				}
				imageView.setBackgroundColor(bg);
				imageView.setImageDrawable(null);
				final BitmapWorkerTask task = new BitmapWorkerTask(imageView, size);
				final AsyncDrawable asyncDrawable = new AsyncDrawable(activity.getResources(), null, task);
				imageView.setImageDrawable(asyncDrawable);
				try {
					task.execute(message);
				} catch (final RejectedExecutionException ignored) {
				}
			}
		}
	}

	public void setHighlightedTerm(List<String> terms) {
		this.highlightedTerm = terms == null ? null : StylingHelper.filterHighlightedWords(terms);
	}

	public interface OnQuoteListener {
		void onQuote(String text);
	}

	public interface OnContactPictureClicked {
		void onContactPictureClicked(Message message);
	}

	public interface OnContactPictureLongClicked {
		void onContactPictureLongClicked(View v, Message message);
	}

    private static class ViewHolder {
        Button loadMoreMessages;
        ImageView editIndicator;
        RelativeLayout audioPlayer;
        LinearLayout messageBox;
		MessageReferenceBinding messageReferenceBinding;
        Button downloadButton;
        ImageView image;
        ImageView indicator;
        ImageView indicatorReceived;
        TextView time;
        CopyTextView messageBody;
        ImageView contactPicture;
        TextView statusMessage;
        TextView encryption;
    }

	static class AsyncDrawable extends BitmapDrawable {
		private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

		public AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
			super(res, bitmap);
			bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
		}

		public BitmapWorkerTask getBitmapWorkerTask() {
			return bitmapWorkerTaskReference.get();
		}
	}

	private class MessageBodyActionModeCallback implements ActionMode.Callback {

		private final TextView textView;

		public MessageBodyActionModeCallback(TextView textView) {
			this.textView = textView;
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			if (onQuoteListener != null) {
				int quoteResId = activity.getThemeResource(R.attr.icon_quote, R.drawable.ic_action_reply);
				// 3rd item is placed after "copy" item
				menu.add(0, android.R.id.button1, 3, R.string.comment_lines).setIcon(quoteResId)
						.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
			}
			return false;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			if (item.getItemId() == android.R.id.button1) {
				int start = textView.getSelectionStart();
				int end = textView.getSelectionEnd();
				if (end > start) {
					String text = transformText(textView.getText(), start, end, false);
					if (onQuoteListener != null) {
						onQuoteListener.onQuote(text);
					}
					mode.finish();
				}
				return true;
			}
			return false;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
		}
	}

	class BitmapWorkerTask extends AsyncTask<Message, Void, Bitmap> {
		private final WeakReference<ImageView> imageViewReference;
		private final int size;
		private Message message = null;

		public BitmapWorkerTask(ImageView imageView, int size) {
			imageViewReference = new WeakReference<>(imageView);
			this.size = size;
		}

		@Override
		protected Bitmap doInBackground(Message... params) {
			this.message = params[0];
			return activity.avatarService().get(this.message, size, isCancelled());
		}

		@Override
		protected void onPostExecute(Bitmap bitmap) {
			if (bitmap != null && !isCancelled()) {
				final ImageView imageView = imageViewReference.get();
				if (imageView != null) {
					imageView.setImageBitmap(bitmap);
					imageView.setBackgroundColor(0x00000000);
				}
			}
		}
	}
}
