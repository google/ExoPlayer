package com.google.android.exoplayer2.source.hls.playlist;

import android.net.Uri;

import com.google.android.exoplayer2.upstream.ParsingLoadable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;

/**
 * Parser for {@link HlsPlaylist}s that reorders the variants based on the comparator passed.
 */
public class ReorderingHlsPlaylistParser implements ParsingLoadable.Parser<HlsPlaylist> {
    private final ParsingLoadable.Parser<HlsPlaylist> playlistParser;
    private final Comparator<HlsMasterPlaylist.HlsUrl> variantComparator;

    /**
     * @param playlistParser the {@link ParsingLoadable.Parser} to wrap.
     * @param variantComparator the {@link Comparator} to use to reorder the variants.
     *                          See {@link HlsMasterPlaylist#copyWithReorderedVariants(Comparator)} for more details.
     */
    public ReorderingHlsPlaylistParser(ParsingLoadable.Parser<HlsPlaylist> playlistParser,
                                       Comparator<HlsMasterPlaylist.HlsUrl> variantComparator) {
        this.playlistParser = playlistParser;
        this.variantComparator = variantComparator;
    }

    @Override
    public HlsPlaylist parse(Uri uri, InputStream inputStream) throws IOException {
        final HlsPlaylist playlist = playlistParser.parse(uri, inputStream);

        if (playlist instanceof HlsMasterPlaylist) {
            return ((HlsMasterPlaylist) playlist).copyWithReorderedVariants(variantComparator);
        }

        return playlist;
    }
}
