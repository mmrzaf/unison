package androidx.media3.common;

public class MediaMetadata {
  public static final int PICTURE_TYPE_FRONT_COVER = 3;
  public final CharSequence title;
  public final CharSequence artist;
  public final CharSequence albumTitle;
  public final CharSequence displayTitle;
  public final CharSequence subtitle;

  public MediaMetadata() { this(null, null, null, null, null); }
  private MediaMetadata(CharSequence title, CharSequence artist, CharSequence albumTitle,
      CharSequence displayTitle, CharSequence subtitle) {
    this.title = title;
    this.artist = artist;
    this.albumTitle = albumTitle;
    this.displayTitle = displayTitle;
    this.subtitle = subtitle;
  }

  public static final class Builder {
    private CharSequence title;
    private CharSequence artist;
    private CharSequence albumTitle;
    private CharSequence displayTitle;
    private CharSequence subtitle;
    public Builder setTitle(CharSequence value) { title = value; return this; }
    public Builder setArtist(CharSequence value) { artist = value; return this; }
    public Builder setAlbumTitle(CharSequence value) { albumTitle = value; return this; }
    public Builder setDisplayTitle(CharSequence value) { displayTitle = value; return this; }
    public Builder setSubtitle(CharSequence value) { subtitle = value; return this; }
    public Builder setArtworkData(byte[] value, int pictureType) { return this; }
    public MediaMetadata build() { return new MediaMetadata(title, artist, albumTitle, displayTitle, subtitle); }
  }
}
