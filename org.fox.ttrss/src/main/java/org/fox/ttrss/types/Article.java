package org.fox.ttrss.types;

import android.os.Parcel;
import android.os.Parcelable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO: serialize Labels
public class Article implements Parcelable {
	public static final int TYPE_AMR_FOOTER = -2;

	public static final int FLAVOR_KIND_ALBUM = 1;
	public static final int FLAVOR_KIND_VIDEO = 2;
	public static final int FLAVOR_KIND_YOUTUBE = 3;

	public static final int SCORE_LOW = -500;
	public static final int SCORE_HIGH = 500;

	public static final int UPDATE_FIELD_MARKED = 0;
	public static final int UPDATE_FIELD_PUBLISHED = 1;
	public static final int UPDATE_FIELD_UNREAD = 2;
	public static final int UPDATE_FIELD_NOTE = 3;
	public static final int UPDATE_FIELD_SCORE = 4;

	public static final int UPDATE_SET_FALSE = 0;
	public static final int UPDATE_SET_TRUE = 1;
	public static final int UPDATE_TOGGLE = 2;

	public int id;
	public boolean unread; 
	public boolean marked;
	public boolean published; 
	public int score;
	public int updated; 
	public boolean is_updated; 
	public String title; 
	public String link; 
	public int feed_id; 
	public List<String> tags;
	public List<Attachment> attachments;
	public String content;
    public String excerpt;
	public List<List<String>> labels;
	public String feed_title;	
	public int comments_count;
	public String comments_link;
	public boolean always_display_attachments;
	public String author;
	public String note;
    public boolean selected;
    public String flavor_image;
    public String flavor_stream;
    public int flavor_kind;
	public String site_url;

	/* not serialized */
	transient public Document articleDoc;
	transient public Element flavorImage;

	transient public String flavorImageUri;
	transient public String flavorStreamUri;
	transient public String youtubeVid;
	transient public List<Element> mediaList = new ArrayList<>();

    public Article(Parcel in) {
		readFromParcel(in);
	}
	
	public Article() {
		
	}

	public void cleanupExcerpt() {
		if (excerpt != null) {
			excerpt = excerpt.replace("&hellip;", "…");
			excerpt = excerpt.replace("]]>", "");
			excerpt = Jsoup.parse(excerpt).text();
		}
	}

	public void collectMediaInfo() {
		if (flavor_image != null && !flavor_image.isEmpty()) {
			flavorImageUri = flavor_image;

			flavorImage = new Element("img")
					.attr("src", flavorImageUri);

			if (flavor_stream != null && !flavor_stream.isEmpty()) {
				flavorStreamUri = flavor_stream;
			}

			return;
		}

		articleDoc = Jsoup.parse(content);

		if (articleDoc != null) {
			mediaList = articleDoc.select("img,video,iframe[src*=youtube.com/embed/]");

			for (Element e : mediaList) {
				if ("iframe".equals(e.tagName().toLowerCase())) {
					flavorImage = e;
					break;
				} /*else if ("video".equals(e.tagName().toLowerCase())) {
					flavorImage = e;
					break;
				}*/
			}

			if (flavorImage == null) {
				for (Element e : mediaList) {
					flavorImage = e;
					break;
				}
			}

			if (flavorImage != null) {
				try {

					if ("video".equals(flavorImage.tagName().toLowerCase())) {
						Element source = flavorImage.select("source").first();

						if (source != null) {
							flavorStreamUri = source.attr("src");
							flavorImageUri = flavorImage.attr("poster");
						}
					} else if ("iframe".equals(flavorImage.tagName().toLowerCase())) {

						String srcEmbed = flavorImage.attr("src");

						if (!srcEmbed.isEmpty()) {
							Pattern pattern = Pattern.compile("/embed/([\\w-]+)");
							Matcher matcher = pattern.matcher(srcEmbed);

							if (matcher.find()) {
								youtubeVid = matcher.group(1);

								flavorImageUri = "https://img.youtube.com/vi/" + youtubeVid + "/hqdefault.jpg";
								flavorStreamUri = "https://youtu.be/" + youtubeVid;
							}
						}
					} else {
						flavorImageUri = flavorImage.attr("src");

						if (!flavorImageUri.isEmpty() && flavorImageUri.startsWith("//")) {
							flavorImageUri = "https:" + flavorImageUri;
						}

						flavorStreamUri = null;
					}
				} catch (Exception e) {
					e.printStackTrace();

					flavorImage = null;
					flavorImageUri = null;
					flavorStreamUri = null;
				}
			}
		}

		if (flavorImageUri == null || flavorImageUri.isEmpty()) {
			// consider attachments
			if (attachments != null) {
				for (Attachment a : attachments) {
					if (a.content_type != null && a.content_type.contains("image/")) {
						flavorImageUri = a.content_url;

						if (flavorImageUri != null && flavorImageUri.startsWith("//")) {
							flavorImageUri = "https:" + flavorImageUri;
						}

						// this is needed for the gallery view
						flavorImage = new Element("img")
								.attr("src", flavorImageUri);

						break;
					}
				}
			}
		}

		//Log.d("Article", "collectMediaInfo: " + flavorImage);
	}

	public Article(int id) {
		this.id = id;
		this.title = "ID:" + id;
		fixNullFields();
	}

	public Article(Article clone) {
		id = clone.id;
		unread = clone.unread;
		marked = clone.marked;
		published = clone.published;
		score = clone.score;
		updated = clone.updated;
		is_updated = clone.is_updated;
		title = clone.title;
		link = clone.link;
		feed_id = clone.feed_id;
		tags = clone.tags;
		attachments = clone.attachments;
		content = clone.content;
		excerpt = clone.excerpt;
		labels = clone.labels;
		feed_title = clone.feed_title;
		comments_count = clone.comments_count;
		comments_link = clone.comments_link;
		always_display_attachments = clone.always_display_attachments;
		author = clone.author;
		note = clone.note;
		selected = clone.selected;
		flavor_image = clone.flavor_image;
		flavor_stream = clone.flavor_stream;
		flavor_kind = clone.flavor_kind;
		site_url = clone.site_url;

		articleDoc = clone.articleDoc;
		flavorImage = clone.flavorImage;

		flavorImageUri = clone.flavorImageUri;
		flavorStreamUri = clone.flavorStreamUri;
		youtubeVid = clone.youtubeVid;
		mediaList = new ArrayList<>(clone.mediaList);
	}

	@Override
	public int describeContents() {
		return 0;
	}
	
	@Override
	public void writeToParcel(Parcel out, int flags) {
		out.writeInt(id);
		out.writeInt(unread ? 1 : 0);
		out.writeInt(marked ? 1 : 0);
		out.writeInt(published ? 1 : 0);
		out.writeInt(score);
		out.writeInt(updated);
		out.writeInt(is_updated ? 1 : 0);
		out.writeString(title);
		out.writeString(link);
		out.writeInt(feed_id);
		out.writeStringList(tags);
		out.writeString(content);
        out.writeString(excerpt);
		out.writeList(attachments);
		out.writeString(feed_title);
		out.writeInt(comments_count);
		out.writeString(comments_link);
		out.writeInt(always_display_attachments ? 1 : 0);
		out.writeString(author);
		out.writeString(note);
        out.writeInt(selected ? 1 : 0);
		out.writeString(site_url);
	}
	
	public void readFromParcel(Parcel in) {
		id = in.readInt();
		unread = in.readInt() == 1;
		marked = in.readInt() == 1;
		published = in.readInt() == 1;
		score = in.readInt();
		updated = in.readInt();
		is_updated = in.readInt() == 1;
		title = in.readString();
		link = in.readString();
		feed_id = in.readInt();
		
		if (tags == null) tags = new ArrayList<>();
		in.readStringList(tags);
		
		content = in.readString();
        excerpt = in.readString();
		
		attachments = new ArrayList<>();
		in.readList(attachments, Attachment.class.getClassLoader());
		
		feed_title = in.readString();
		
		comments_count = in.readInt();
		comments_link = in.readString();
		always_display_attachments = in.readInt() == 1;
		author = in.readString();
		note = in.readString();
        selected = in.readInt() == 1;
		site_url = in.readString();
	}

	public boolean equalsById(Article article) {
		if (article != null && id == article.id) {
			return true;
		} else {
			return false;
		}
	}

	@SuppressWarnings("rawtypes")
	public static final Parcelable.Creator CREATOR =
    	new Parcelable.Creator() {
            public Article createFromParcel(Parcel in) {
                return new Article(in);
            }
 
            public Article[] newArray(int size) {
                return new Article[size];
            }
        };

	/** set fields which might be missing during JSON deserialization to sane values */
	public void fixNullFields() {
		if (note == null) note = "";
		if (link == null) link = "";
		if (tags == null) tags = new ArrayList<>();
		if (note == null) note = "";
		if (excerpt == null) excerpt = "";
		if (content == null) content = "";
	}
}
