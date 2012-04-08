/*
 * Copyright Matt Palmer 2012, All rights reserved.
 * 
 * This code is licensed under a standard 3-clause BSD license:
 * 
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 *  * Redistributions of source code must retain the above copyright notice, 
 *    this list of conditions and the following disclaimer.
 * 
 *  * Redistributions in binary form must reproduce the above copyright notice, 
 *    this list of conditions and the following disclaimer in the documentation 
 *    and/or other materials provided with the distribution.
 * 
 *  * The names of its contributors may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.domesdaybook.searcher.multisequence;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import net.domesdaybook.matcher.multisequence.MultiSequenceMatcher;
import net.domesdaybook.matcher.sequence.SequenceMatcher;
import net.domesdaybook.reader.Reader;
import net.domesdaybook.reader.Window;
import net.domesdaybook.searcher.SearchResult;
import net.domesdaybook.searcher.SearchUtils;
import net.domesdaybook.searcher.multisequence.AbstractWuManberSearcher.SearchInfo;

/**
 *
 * @author Matt Palmer
 */
public class WuManberOneByteTunedSearcher extends AbstractWuManberTunedSearcher {
        
    public WuManberOneByteTunedSearcher(final MultiSequenceMatcher matcher) {
        super(matcher, 1);
    }


    @Override
    public List<SearchResult<SequenceMatcher>> searchForwards(final byte[] bytes, 
            final int fromPosition, final int toPosition) {
        // Get info needed to search with:
        final SearchInfo info = forwardInfo.get();
        final int[] safeShifts = info.shifts;
        final int[] finalShifts = info.finalShifts;
        final MultiSequenceMatcher backMatcher = info.matcher;
        final int finalHashBitMask = finalShifts.length - 1;
        
        // Calculate safe bounds for the search:
        final int minimumLength = sequences.getMinimumLength();
        final int minimumPosition = minimumLength - 1;        
        final int lastPossiblePosition = bytes.length - 1;
        final int lastPossibleUnrolledPosition = lastPossiblePosition - 3 * minimumLength;
        final int lastToPosition = toPosition + sequences.getMaximumLength() - 1;
        final int lastUnrolledPosition = lastToPosition < lastPossibleUnrolledPosition?
                                         lastToPosition : lastPossibleUnrolledPosition;
        int searchPosition = fromPosition > 0 ?
                             fromPosition + minimumPosition : minimumPosition;
        
        // Search forwards using the unrolled version of the algorithm.  This can
        // only search up to 3 minimum lengths from the end of the array, or we
        // risk an ArrayOutOfBoundsException.
        UNROLLED: while (searchPosition <= lastUnrolledPosition) {

            // Unroll skip loop (if shift is zero, then searchposition won't change).
            // Could cross over end of byte array however, so this search loop
            // will never search closer than 3 max shifts (minimum length)
            // to the end of the array, to avoid a possible ArrayIndexOutOfBoundsException.
            int lastByteValue = bytes[searchPosition] & 0xFF;
            int safeShift = safeShifts[lastByteValue];
            while (safeShift != 0) {
                searchPosition += safeShift;
                searchPosition += safeShifts[bytes[searchPosition] & 0xFF];
                searchPosition += safeShifts[bytes[searchPosition] & 0xFF]; 
                if (searchPosition > lastUnrolledPosition) {
                    break UNROLLED;
                }
                lastByteValue = bytes[searchPosition] & 0xFF;
                safeShift = safeShifts[lastByteValue];
            }

            // No safe shift - see if we have any matches:
            final Collection<SequenceMatcher> matches =
                    backMatcher.allMatchesBackwards(bytes, searchPosition);
            if (!matches.isEmpty()) {

                // See if any of the matches are within the bounds of the search:
                final List<SearchResult<SequenceMatcher>> results = 
                    SearchUtils.resultsBackFromPosition(searchPosition, matches, 
                                                        fromPosition, toPosition);
                if (!results.isEmpty()) {
                    return results;
                }
            }
            
            searchPosition += finalShifts[lastByteValue & finalHashBitMask];
        }
        
        // If we need to search past the last unrolled position, we need to use an
        // unrolled version:
        final int lastPosition = lastToPosition < lastPossiblePosition ?
                                 lastToPosition : lastPossiblePosition;
        while (searchPosition <= lastPosition) {
            final int lastByteValue = bytes[searchPosition & 0xFF];
            int safeShift = safeShifts[lastByteValue];   
            if (safeShift > 0) {
                searchPosition += safeShift;
            } else {
                // No safe shift - see if we have any matches:
                final Collection<SequenceMatcher> matches =
                        backMatcher.allMatchesBackwards(bytes, searchPosition);
                if (!matches.isEmpty()) {

                    // See if any of the matches are within the bounds of the search:
                    final List<SearchResult<SequenceMatcher>> results = 
                        SearchUtils.resultsBackFromPosition(searchPosition, matches, 
                                                            fromPosition, toPosition);
                    if (!results.isEmpty()) {
                        return results;
                    }
                }
                searchPosition += finalShifts[lastByteValue & finalHashBitMask];
            }
        }

        return SearchUtils.noResults();
    }


    @Override
    protected List<SearchResult<SequenceMatcher>> doSearchForwards(final Reader reader, 
            final long fromPosition, final long toPosition) throws IOException {
        // Get info needed to search with:
        final SearchInfo info = forwardInfo.get();
        final int[] safeShifts = info.shifts;
        final MultiSequenceMatcher backMatcher = info.matcher;

        // Initialise window search:
        final long finalPosition = toPosition + sequences.getMaximumLength() - 1;
        long searchPosition = fromPosition + sequences.getMinimumLength() - 1;       


        // While there is a window to search in:
        Window window;             
        while (searchPosition <= finalPosition &&
               (window = reader.getWindow(searchPosition)) != null) {

            // Initialise array search:
            final byte[] array = window.getArray();
            final int arrayStartPosition = reader.getWindowOffset(searchPosition);
            final int arrayEndPosition = window.length() - 1;
            final long distanceToEnd = finalPosition - window.getWindowPosition();     
            final int lastSearchPosition = distanceToEnd < arrayEndPosition?
                                     (int) distanceToEnd : arrayEndPosition;
            int arraySearchPosition = arrayStartPosition;            

            // Search forwards in this array:
            while (arraySearchPosition <= lastSearchPosition) {

                final int safeShift = safeShifts[array[arraySearchPosition] & 0xFF];
                if (safeShift == 0) {
                    // see if we have a match:
                    final long matchEndPosition = searchPosition + arraySearchPosition - arrayStartPosition;
                    final Collection<SequenceMatcher> matches =
                            backMatcher.allMatchesBackwards(reader, matchEndPosition);
                    if (!matches.isEmpty()) {
                        // See if any of the matches are within the bounds of the search:
                        final List<SearchResult<SequenceMatcher>> results = 
                            SearchUtils.resultsBackFromPosition(matchEndPosition, matches,
                                                                fromPosition, toPosition);
                        if (!results.isEmpty()) {
                            return results;
                        }
                    }
                    arraySearchPosition++;
                } else {
                    arraySearchPosition += safeShift;
                } 
            } 

            // No match was found in this array - calculate the current search position:
            searchPosition += arraySearchPosition - arrayStartPosition;
        }

        return SearchUtils.noResults();                    
    }


    @Override
    public List<SearchResult<SequenceMatcher>> searchBackwards(final byte[] bytes, 
            final int fromPosition, final int toPosition) {
        // Get info needed to search with:
        final SearchInfo info = backwardInfo.get();
        final int[] safeShifts = info.shifts;
        final MultiSequenceMatcher verifier = info.matcher;

        // Calculate safe bounds for the search:
        final int lastPosition = toPosition > 0 ?
                                 toPosition : 0;
        final int firstPossiblePosition = bytes.length - 1;
        int searchPosition = fromPosition < firstPossiblePosition ?
                             fromPosition : firstPossiblePosition;

        // Search backwards:
        while (searchPosition >= lastPosition) {

            // Get the safe shift for this byte:
            final int safeShift = safeShifts[bytes[searchPosition] & 0xFF];

            // Can we shift safely?
            if (safeShift == 0) {

                // No safe shift - see if we have any matches:
                final Collection<SequenceMatcher> matches =
                        verifier.allMatches(bytes, searchPosition);
                if (!matches.isEmpty()) {
                    return SearchUtils.resultsAtPosition(searchPosition, matches);
                }
                searchPosition--; // no safe shift other than to advance one on.

            } else { // we have a safe shift, move on:
                searchPosition -= safeShift; 
            }
        }
        return SearchUtils.noResults();
    }


    @Override
    protected List<SearchResult<SequenceMatcher>> doSearchBackwards(final Reader reader, 
            final long fromPosition, final long toPosition) throws IOException {
        // Get the objects needed to search:
        final SearchInfo info = backwardInfo.get();
        final int[] safeShifts = info.shifts;
        final MultiSequenceMatcher verifier = info.matcher;        

        // Initialise window search:
        long searchPosition = fromPosition;

        // Search backwards across the windows:
        Window window;
        while (searchPosition >= toPosition &&
               (window = reader.getWindow(searchPosition)) != null) {

            // Initialise the window search:
            final byte[] array = window.getArray();
            final int arrayStartPosition = reader.getWindowOffset(searchPosition);   
            final long distanceToEnd = toPosition - window.getWindowPosition();
            final int lastSearchPosition = distanceToEnd > 0?
                                     (int) distanceToEnd : 0;
            int arraySearchPosition = arrayStartPosition;

            // Search using the byte array for shifts, using the Reader
            // for verifiying the sequence with the sequences:          
            while (arraySearchPosition >= lastSearchPosition) {

                final int safeShift = safeShifts[array[arraySearchPosition] & 0xFF];
                if (safeShift == 0) {

                    // The first byte matched - verify the rest of the sequences.
                    final long startMatchPosition = searchPosition + arraySearchPosition - arrayStartPosition;
                    final Collection<SequenceMatcher> matches = verifier.allMatches(reader, startMatchPosition);
                    if (!matches.isEmpty()) {
                        return SearchUtils.resultsAtPosition(startMatchPosition, matches); // match found.
                    }

                    arraySearchPosition--; // no match, shift back one.
                } else { // No match was found - shift backward by the shift for the current byte:
                    arraySearchPosition -= safeShift;
                }
            }

            // No match was found in this array - calculate the current search position:
            searchPosition -= (arrayStartPosition - arraySearchPosition);
        }

        return SearchUtils.noResults();

    }
        
}
