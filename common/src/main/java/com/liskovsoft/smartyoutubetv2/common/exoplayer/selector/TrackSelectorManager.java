package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.Parameters;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection.Definition;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.AudioTrack;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.VideoTrack;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.selector.RestoreTrackSelector;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.selector.RestoreTrackSelector.TrackSelectorCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class TrackSelectorManager implements TrackSelectorCallback {
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    public static final int RENDERER_INDEX_VIDEO = 0;
    public static final int RENDERER_INDEX_AUDIO = 1;
    public static final int RENDERER_INDEX_SUBTITLE = 2;
    //private static final TrackSelection.Factory FIXED_FACTORY = new FixedTrackSelection.Factory();
    //private static final TrackSelection.Factory RANDOM_FACTORY = new RandomTrackSelection.Factory();
    private static final String TAG = TrackSelectorManager.class.getSimpleName();
    private final String mLanguage;

    private DefaultTrackSelector mTrackSelector;
    //private TrackSelection.Factory mTrackSelectionFactory;

    private final Renderer[] mRenderers = new Renderer[3];
    private final MediaTrack[] mSelectedTracks = new MediaTrack[3];
    private long mTracksInitTimeMs;

    public TrackSelectorManager(String language) {
        mLanguage = language;
    }

    public void invalidate() {
        Arrays.fill(mRenderers, null);
    }

    /**
     * Shows the selection dialog for a given renderer.
     * @param rendererIndex The index of the renderer. <br/>
     *                      One of the {@link #RENDERER_INDEX_VIDEO}, {@link #RENDERER_INDEX_AUDIO}, {@link #RENDERER_INDEX_SUBTITLE}
     */
    private Set<MediaTrack> getAvailableTracks(int rendererIndex) {
        initRenderer(rendererIndex);

        if (mRenderers[rendererIndex] == null) {
            return null;
        }

        return mRenderers[rendererIndex].sortedTracks;
    }

    /**
     * Creates renderer structure.
     * @param rendererIndex The index of the renderer. <br/>
     *                      One of the {@link #RENDERER_INDEX_VIDEO}, {@link #RENDERER_INDEX_AUDIO}, {@link #RENDERER_INDEX_SUBTITLE}
     */
    private void initRenderer(int rendererIndex) {
        if (mRenderers[rendererIndex] != null && mRenderers[rendererIndex].mediaTracks != null) {
            return;
        }

        if (mTrackSelector == null) {
            Log.e(TAG, "Can't init renderer %s. TrackSelector is null!", rendererIndex);
            return;
        }

        initTrackGroups(rendererIndex, mTrackSelector.getCurrentMappedTrackInfo(), mTrackSelector.getParameters());
        initMediaTracks(rendererIndex);
    }

    /**
     * Creates renderer structure.
     * @param rendererIndex The index of the renderer. <br/>
     *                      One of the {@link #RENDERER_INDEX_VIDEO}, {@link #RENDERER_INDEX_AUDIO}, {@link #RENDERER_INDEX_SUBTITLE}
     * @param trackInfo supplied externally from {@link RestoreTrackSelector}
     * @param parameters supplied externally from {@link RestoreTrackSelector}
     */
    private void initRenderer(int rendererIndex, MappedTrackInfo trackInfo, Parameters parameters) {
        if (mRenderers[rendererIndex] != null && mRenderers[rendererIndex].mediaTracks != null) {
            return;
        }

        initTrackGroups(rendererIndex, trackInfo, parameters);
        initMediaTracks(rendererIndex);
    }

    /**
     * Creates renderer structure.
     * @param rendererIndex The index of the renderer. <br/>
     *                      One of the {@link #RENDERER_INDEX_VIDEO}, {@link #RENDERER_INDEX_AUDIO}, {@link #RENDERER_INDEX_SUBTITLE}
     * @param groups supplied externally from {@link RestoreTrackSelector}
     * @param parameters supplied externally from {@link RestoreTrackSelector}
     */
    private void initRenderer(int rendererIndex, TrackGroupArray groups, Parameters parameters) {
        if (mRenderers[rendererIndex] != null && mRenderers[rendererIndex].mediaTracks != null) {
            return;
        }

        initTrackGroups(rendererIndex, groups, parameters);
        initMediaTracks(rendererIndex);
    }

    private void initTrackGroups(int rendererIndex, MappedTrackInfo trackInfo, Parameters parameters) {
        if (trackInfo == null) {
            Log.e(TAG, "Can't perform track selection. Mapped track info isn't initialized yet!");
            return;
        }

        if (parameters == null) {
            Log.e(TAG, "Can't perform track selection. Track parameters isn't initialized yet!");
            return;
        }

        TrackGroupArray trackGroups = trackInfo.getTrackGroups(rendererIndex);

        initTrackGroups(rendererIndex, trackGroups, parameters);
    }

    private void initTrackGroups(int rendererIndex, TrackGroupArray trackGroups, Parameters parameters) {
        Renderer renderer = new Renderer();
        mRenderers[rendererIndex] = renderer;

        renderer.trackGroups = trackGroups;
        renderer.isDisabled = parameters.getRendererDisabled(rendererIndex);
    }

    private void initMediaTracks(int rendererIndex) {
        if (mRenderers[rendererIndex] == null) {
            return;
        }

        Renderer renderer = mRenderers[rendererIndex];
        renderer.mediaTracks = new MediaTrack[renderer.trackGroups.length][];
        // Fix for java.util.ConcurrentModificationException inside of:
        // com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem.from (ExoFormatItem.java:44)
        // Won't help: renderer.sortedTracks = Collections.synchronizedSortedSet(new TreeSet<>(new MediaTrackFormatComparator()));
        renderer.sortedTracks = new TreeSet<>(new MediaTrackFormatComparator());

        if (rendererIndex == RENDERER_INDEX_SUBTITLE) {
            // AUTO OPTION: add disable subs option
            MediaTrack noSubsTrack = MediaTrack.forRendererIndex(rendererIndex);
            // Temporal selection.
            // Real selection will be override later on setSelection() routine.
            noSubsTrack.isSelected = true;
            renderer.sortedTracks.add(noSubsTrack);
            renderer.selectedTrack = noSubsTrack;
        }

        for (int groupIndex = 0; groupIndex < renderer.trackGroups.length; groupIndex++) {
            TrackGroup group = renderer.trackGroups.get(groupIndex);
            renderer.mediaTracks[groupIndex] = new MediaTrack[group.length];

            for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
                Format format = group.getFormat(trackIndex);

                MediaTrack mediaTrack = MediaTrack.forRendererIndex(rendererIndex);
                mediaTrack.format = format;
                mediaTrack.groupIndex = groupIndex;
                mediaTrack.trackIndex = trackIndex;

                if (mediaTrack.isVP9Codec() && !Helpers.isVP9ResolutionSupported(mediaTrack.getHeight())) {
                    continue;
                }

                if (mediaTrack.isAV1Codec() && !Helpers.isAV1ResolutionSupported(mediaTrack.getHeight())) {
                    continue;
                }

                // Selected track or not will be decided later in setSelection() routine

                renderer.mediaTracks[groupIndex][trackIndex] = mediaTrack;
                renderer.sortedTracks.add(mediaTrack);
            }
        }

        mTracksInitTimeMs = System.currentTimeMillis();
    }

    /**
     * We need to circle through the tracks to remove previously selected marks
     */
    private void setSelection(int rendererIndex, int trackGroupIndex, int... trackIndexes) {
        if (mRenderers[rendererIndex] == null) {
            return;
        }

        // Adaptive selection should be disabled in RestoreTrackSelector (e.g trackIndexes.length == 1)

        // We need to circle through the tracks to remove previously selected marks.

        Renderer renderer = mRenderers[rendererIndex];
        renderer.selectedTrack = null;
        for (int groupIndex = 0; groupIndex < renderer.mediaTracks.length; groupIndex++) {
            MediaTrack[] trackGroup = renderer.mediaTracks[groupIndex];

            if (trackGroup == null) {
                continue;
            }

            for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
                MediaTrack mediaTrack = trackGroup[trackIndex];

                if (mediaTrack == null) {
                    continue;
                }

                mediaTrack.isSelected = groupIndex == trackGroupIndex && Helpers.equalsAny(trackIndex, trackIndexes);

                if (mediaTrack.isSelected) {
                    renderer.selectedTrack = mediaTrack;
                }
            }
        }

        // Special handling for tracks with auto option
        if (rendererIndex == RENDERER_INDEX_SUBTITLE) { // no subs selected
            MediaTrack noSubsTrack = renderer.sortedTracks.first();

            noSubsTrack.isSelected = renderer.selectedTrack == null;
            renderer.selectedTrack = renderer.selectedTrack != null ? renderer.selectedTrack : noSubsTrack;
        }
    }

    private void enableAutoSelection(int rendererIndex) {
        mRenderers[rendererIndex].isDisabled = false;
        mRenderers[rendererIndex].selectedTrack = null;
    }

    public Set<MediaTrack> getVideoTracks() {
        return getAvailableTracks(RENDERER_INDEX_VIDEO);
    }

    public Set<MediaTrack> getAudioTracks() {
        return getAvailableTracks(RENDERER_INDEX_AUDIO);
    }

    public Set<MediaTrack> getSubtitleTracks() {
        return getAvailableTracks(RENDERER_INDEX_SUBTITLE);
    }

    private Pair<Definition, MediaTrack> createSelection(TrackGroupArray groups, MediaTrack selectedTrack) {
        if (selectedTrack == null) {
            Log.e(TAG, "Can't create selection. Selected track is null.");
            return null;
        }

        if (mRenderers[selectedTrack.rendererIndex] == null) {
            Log.e(TAG, "Can't create selection. Renderer isn't initialized.");
            return null;
        }

        Pair<Definition, MediaTrack> definitionPair = null;

        MediaTrack matchedTrack = findBestMatch(selectedTrack);

        if (matchedTrack.groupIndex != -1) {
            Definition definition = new Definition(groups.get(matchedTrack.groupIndex), matchedTrack.trackIndex);
            definitionPair = new Pair<>(definition, matchedTrack);
            setSelection(matchedTrack.rendererIndex, matchedTrack.groupIndex, matchedTrack.trackIndex);
        } else {
            Log.e(TAG, "Can't create selection. No match for the track %s", selectedTrack);
        }

        return definitionPair;
    }

    private Pair<Definition, MediaTrack> createRendererSelection(int rendererIndex, TrackGroupArray groups, Parameters params) {
        if (mSelectedTracks[rendererIndex] == null || params.hasSelectionOverride(rendererIndex, groups)) {
            return null;
        }

        initRenderer(rendererIndex, groups, params);
        return createSelection(groups, mSelectedTracks[rendererIndex]);
    }

    private void updateRendererSelection(int rendererIndex, TrackGroupArray groups, Parameters params, Definition definition) {
        initRenderer(rendererIndex, groups, params);

        definition = getOverride(rendererIndex, groups, params, definition);
        
        setSelection(rendererIndex, groups.indexOf(definition.group), definition.tracks);
    }

    private Definition getOverride(int rendererIndex, TrackGroupArray rendererTrackGroups, Parameters params, Definition original) {
        Definition definition = original;

        if (params.hasSelectionOverride(rendererIndex, rendererTrackGroups)) {
            SelectionOverride override = params.getSelectionOverride(rendererIndex, rendererTrackGroups);

            if (override != null) {
                definition = new TrackSelection.Definition(
                                rendererTrackGroups.get(override.groupIndex),
                                override.tracks,
                                override.reason,
                                override.data);
            }
        }

        return definition;
    }

    @Override
    public Pair<Definition, MediaTrack> onSelectVideoTrack(TrackGroupArray groups, Parameters params) {
        return createRendererSelection(RENDERER_INDEX_VIDEO, groups, params);
    }

    @Override
    public Pair<Definition, MediaTrack> onSelectAudioTrack(TrackGroupArray groups, Parameters params) {
        return createRendererSelection(RENDERER_INDEX_AUDIO, groups, params);
    }

    @Override
    public Pair<Definition, MediaTrack> onSelectSubtitleTrack(TrackGroupArray groups, Parameters params) {
        return createRendererSelection(RENDERER_INDEX_SUBTITLE, groups, params);
    }

    @Override
    public void updateVideoTrackSelection(TrackGroupArray groups, Parameters params, Definition definition) {
        updateRendererSelection(RENDERER_INDEX_VIDEO, groups, params, definition);
    }

    @Override
    public void updateAudioTrackSelection(TrackGroupArray groups, Parameters params, Definition definition) {
        updateRendererSelection(RENDERER_INDEX_AUDIO, groups, params, definition);
    }

    @Override
    public void updateSubtitleTrackSelection(TrackGroupArray groups, Parameters params, Definition definition) {
        updateRendererSelection(RENDERER_INDEX_SUBTITLE, groups, params, definition);
    }

    public void selectTrack(MediaTrack track) {
        if (track == null) {
            return;
        }

        int rendererIndex = track.rendererIndex;

        initRenderer(rendererIndex);

        mSelectedTracks[rendererIndex] = track;

        if (mRenderers[rendererIndex] == null || mRenderers[rendererIndex].mediaTracks == null) {
            Log.e(TAG, "Renderer isn't initialized. Waiting for later selection...");
            return;
        }

        // enable renderer
        mRenderers[rendererIndex].isDisabled = false;

        MediaTrack matchedTrack = findBestMatch(track);
        
        setSelection(matchedTrack.rendererIndex, matchedTrack.groupIndex, matchedTrack.trackIndex);

        // save immediately
        applyOverride(rendererIndex);
    }

    public MediaTrack getVideoTrack() {
        initRenderer(RENDERER_INDEX_VIDEO);

        Renderer renderer = mRenderers[RENDERER_INDEX_VIDEO];

        if (renderer == null) {
            return null;
        }

        return renderer.selectedTrack;
    }

    /**
     *  Video/audio tracks should be selected at this point.<br/>
     *  Reselect if not done yet.
     */
    public void fixTracksSelection() {
        for (MediaTrack track : mSelectedTracks) {
            if (track == null || track.rendererIndex == RENDERER_INDEX_SUBTITLE) {
                continue;
            }

            if (!hasSelection(track.rendererIndex)) {
                Log.e(TAG, "Oops. Track %s isn't selected before. Fixing...", track.rendererIndex);
                selectTrack(track);
            }
        }
    }

    public void setTrackSelector(DefaultTrackSelector selector) {
        Log.d(TAG, "Initializing TrackSelector...");
        mTrackSelector = selector;

        if (selector instanceof RestoreTrackSelector) {
            ((RestoreTrackSelector) selector).setOnTrackSelectCallback(this);
        }
    }

    public void release() {
        if (mTrackSelector != null) {
            Log.d(TAG, "Destroying TrackSelector...");
            if (mTrackSelector instanceof RestoreTrackSelector) {
                ((RestoreTrackSelector) mTrackSelector).setOnTrackSelectCallback(null);
            }
            mTrackSelector = null;
        }

        invalidate();
    }

    private MediaTrack findBestMatch(MediaTrack originTrack) {
        Log.d(TAG, "findBestMatch: Starting: " + originTrack.format);

        Renderer renderer = mRenderers[originTrack.rendererIndex];

        MediaTrack result = createAutoSelection(originTrack.rendererIndex);

        if (originTrack.format != null) { // not auto selection
            MediaTrack prevResult;

            MediaTrack[][] mediaTracks = filterByLanguage(renderer.mediaTracks, originTrack);

            for (int groupIndex = 0; groupIndex < mediaTracks.length; groupIndex++) {
                prevResult = result;

                // Very rare NPE fix
                MediaTrack[] trackGroup = mediaTracks[groupIndex];

                if (trackGroup == null) {
                    Log.e(TAG, "Track selection error. Media track group %s is empty.", groupIndex);
                    continue;
                }

                for (MediaTrack mediaTrack : trackGroup) {
                    if (mediaTrack == null) {
                        continue;
                    }

                    int compare = originTrack.inBounds(mediaTrack);

                    if (compare == 0) {
                        Log.d(TAG, "findBestMatch: Found exact match by size and fps in list: " + mediaTrack.format);

                        // Get ready for group with multiple codecs: avc, av01
                        if (MediaTrack.codecEquals(mediaTrack, originTrack)) {
                            result = mediaTrack;
                            // Don't do break for VideoTrack because we don't know whether there 30/60 fps.
                            if (!(originTrack instanceof VideoTrack)) {
                                break;
                            }
                        } else if (!MediaTrack.codecEquals(result, originTrack) && !MediaTrack.preferByCodec(result, mediaTrack)) {
                            result = mediaTrack;
                        }
                    } else if (compare > 0) {
                        // Select track with higher possible quality or by preferred codec
                        boolean higherQuality = mediaTrack.compare(result) >= 0;
                        //boolean preferByCodec = MediaTrack.preferByCodec(mediaTrack, result);
                        if (higherQuality) { // || preferByCodec
                            // Get ready for group with multiple codecs: avc, av01
                            // Also handle situations where avc and av01 only (no vp9). E.g.: B4mIhE_15nc
                            if (MediaTrack.codecEquals(mediaTrack, originTrack)) {
                                result = mediaTrack;
                            } else if (!MediaTrack.codecEquals(result, originTrack) && !MediaTrack.preferByCodec(result, mediaTrack)) {
                                result = mediaTrack;
                            }
                        }
                    }
                }

                // Don't let change the codec beside needed one.
                // Handle situation where same codecs in different groups (e.g. subtitles).
                if (MediaTrack.codecEquals(result, originTrack)) {
                    if (originTrack.compare(result) == 0) { // Exact match found
                        break;
                    }

                    if (MediaTrack.codecEquals(prevResult, originTrack) && prevResult.compare(result) > 0) {
                        result = prevResult;
                    }
                } else if (MediaTrack.codecEquals(prevResult, originTrack)) {
                    result = prevResult;
                } else if (prevResult.compare(result) == 0) { // Formats are the same except the codecs
                    if (MediaTrack.preferByCodec(prevResult, result)) {
                        result = prevResult;
                    }
                }
            }
        }

        Log.d(TAG, "findBestMatch: Found: " + result.format);

        return result;
    }

    private void applyOverride(int rendererIndex) {
        Renderer renderer = mRenderers[rendererIndex];

        if (renderer == null) {
            return;
        }

        mTrackSelector.setParameters(mTrackSelector.buildUponParameters().setRendererDisabled(rendererIndex, renderer.isDisabled));

        MediaTrack selectedTrack = renderer.selectedTrack;

        if (selectedTrack != null && selectedTrack.groupIndex != -1) {
            Log.d(TAG, "Setting override for renderer %s, group %s, track %s...", rendererIndex, selectedTrack.groupIndex, selectedTrack.trackIndex);

            mTrackSelector.setParameters(mTrackSelector.buildUponParameters().setSelectionOverride(
                    rendererIndex, renderer.trackGroups, new SelectionOverride(selectedTrack.groupIndex, selectedTrack.trackIndex)
            ));
        } else {
            Log.e(TAG, "Something went wrong. Selected track not found");
            mTrackSelector.setParameters(mTrackSelector.buildUponParameters().clearSelectionOverrides(rendererIndex)); // Auto quality button selected
        }
    }

    private MediaTrack createAutoSelection(int rendererIndex) {
        return MediaTrack.forRendererIndex(rendererIndex);
    }

    private boolean hasSelection(int rendererIndex) {
        return mRenderers[rendererIndex] != null && mRenderers[rendererIndex].selectedTrack != null;
    }

    /**
     * Trying to filter languages preferred by the user
     */
    private MediaTrack[][] filterByLanguage(MediaTrack[][] trackGroupList, MediaTrack originTrack) {
        if (!(originTrack instanceof AudioTrack) || mLanguage == null || trackGroupList.length <= 1) {
            return trackGroupList;
        }

        if (originTrack.format != null && originTrack.format.language != null) {
            return trackGroupList;
        }

        List<MediaTrack[]> resultTracks = null;
        List<MediaTrack[]> resultTracksFallback = null;

        // Tracks are grouped by the language/formats
        for (MediaTrack[] trackGroup : trackGroupList) {
            if (trackGroup != null && trackGroup.length >= 1) {
                MediaTrack mediaTrack = trackGroup[0];

                if (mediaTrack.format != null) {
                    if (Helpers.equals(mediaTrack.format.language, mLanguage)) {
                        if (resultTracks == null) {
                            resultTracks = new ArrayList<>();
                        }

                        resultTracks.add(trackGroup);
                    } else if (Helpers.equals(mediaTrack.format.language, "en")) {
                        if (resultTracksFallback == null) {
                            resultTracksFallback = new ArrayList<>();
                        }

                        resultTracksFallback.add(trackGroup);
                    }
                }
            }
        }

        if (resultTracks != null && !resultTracks.isEmpty()) {
            return resultTracks.toArray(new MediaTrack[0][]);
        }

        if (resultTracksFallback != null && !resultTracksFallback.isEmpty()) {
            return resultTracksFallback.toArray(new MediaTrack[0][]);
        }

        return trackGroupList;
    }

    //private void setOverride(int rendererIndex, int group, int[] tracks, boolean enableRandomAdaptation) {
    //    TrackSelection.Factory factory = tracks.length == 1 ? FIXED_FACTORY : (enableRandomAdaptation ? RANDOM_FACTORY : mTrackSelectionFactory);
    //    mRenderers[rendererIndex].override = new SelectionOverride(group, tracks);
    //}

    // Track array manipulation.
    private static int[] getTracksAdding(SelectionOverride override, int addedTrack) {
        int[] tracks = override.tracks;
        tracks = Arrays.copyOf(tracks, tracks.length + 1);
        tracks[tracks.length - 1] = addedTrack;
        return tracks;
    }

    private static int[] getTracksRemoving(SelectionOverride override, int removedTrack) {
        int[] tracks = new int[override.length - 1];
        int trackCount = 0;
        for (int i = 0; i < tracks.length + 1; i++) {
            int track = override.tracks[i];
            if (track != removedTrack) {
                tracks[trackCount++] = track;
            }
        }
        return tracks;
    }

    /**
     * Get the number of tracks with the same resolution.
     * <p>I assume that the tracks already have been sorted in descendants order. <br/>
     * <p>Details: {@code com.liskovsoft.smartyoutubetv.flavors.exoplayer.youtubeinfoparser.mpdbuilder.MyMPDBuilder}
     * @param group the group
     * @param trackIndex current track in group
     * @return
     */
    private int getRelatedTrackOffsets(TrackGroup group, int trackIndex) {
        int prevHeight = 0;
        int offset = 0;
        for (int i = trackIndex; i > 0; i--) {
            Format format = group.getFormat(i);
            if (prevHeight == 0) {
                prevHeight = format.height;
            } else if (prevHeight == format.height) {
                offset++;
            } else {
                break;
            }
        }
        return offset;
    }

    private static class Renderer {
        public boolean isDisabled;
        public TrackGroupArray trackGroups;
        public MediaTrack[][] mediaTracks;
        public SortedSet<MediaTrack> sortedTracks;
        public MediaTrack selectedTrack;
    }

    private static class MediaTrackFormatComparator implements Comparator<MediaTrack> {
        @Override
        public int compare(MediaTrack mediaTrack1, MediaTrack mediaTrack2) {
            Format format1 = mediaTrack1.format;
            Format format2 = mediaTrack2.format;

            if (format1 == null) { // assume it's auto option
                return -1;
            }

            if (format2 == null) { // assume it's auto option
                return 1;
            }

            // sort subtitles/audio tracks by language code
            if (format1.language != null && format2.language != null) {
                int result = format1.language.compareTo(format2.language);
                if (result != 0) {
                    return result;
                }
            }

            int leftVal = format2.width + (int) format2.frameRate + MediaTrack.getCodecWeight(format2.codecs);
            int rightVal = format1.width + (int) format1.frameRate + MediaTrack.getCodecWeight(format1.codecs);

            int delta = leftVal - rightVal;
            if (delta == 0) {
                return format2.bitrate - format1.bitrate;
            }

            return leftVal - rightVal;
        }
    }
}
