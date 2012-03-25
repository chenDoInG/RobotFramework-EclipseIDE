/**
 * Copyright 2012 Nitor Creations Oy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nitorcreations.robotframework.eclipseide.internal.assistant;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ContextInformation;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;

import com.nitorcreations.robotframework.eclipseide.builder.parser.LineType;
import com.nitorcreations.robotframework.eclipseide.builder.parser.RobotLine;
import com.nitorcreations.robotframework.eclipseide.builder.parser.RobotFile;
import com.nitorcreations.robotframework.eclipseide.editors.ResourceManager;
import com.nitorcreations.robotframework.eclipseide.internal.rules.RobotWhitespace;
import com.nitorcreations.robotframework.eclipseide.internal.util.DefinitionFinder;
import com.nitorcreations.robotframework.eclipseide.structure.ParsedString;

public class RobotContentAssistant implements IContentAssistProcessor {

    String[] fgProposals = { "test1", "test2" };

    // ctrl-space completion proposals
    @Override
    public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int documentOffset) {
        // find info about current line
        IRegion lineInfo;
        String line;
        int lineNo;
        IDocument document = viewer.getDocument();
        try {
            lineInfo = document.getLineInformationOfOffset(documentOffset);
            lineNo = document.getLineOfOffset(documentOffset);
            line = document.get(lineInfo.getOffset(), lineInfo.getLength());
        } catch (BadLocationException ex) {
            return null;
        }

        List<RobotLine> lines = RobotFile.get(document).getLines();
        RobotLine rfeLine = lines.get(lineNo);

        // find the cursor location range inside the current line where keyword
        // completion proposals make sense
        // TODO this only works for basic keyword calls, [Setup], FOR-indented,
        // etc unsupported atm
        int leftPos = findLeftmostKeywordPosition(lineInfo, line, rfeLine);
        int rightPos = findRightmostKeywordPosition(lineInfo, line, rfeLine);
        int replacePos = rfeLine.arguments.size() >= 2 ? rfeLine.arguments.get(1).getArgCharPos() - lineInfo.getOffset() : leftPos;
        int cursorPos = documentOffset - lineInfo.getOffset();
        // if inside range, return keyword proposals
        if (leftPos <= cursorPos && cursorPos <= rightPos) {
            return computeKeywordCompletionProposals(viewer, document, documentOffset, rfeLine, leftPos, rightPos, replacePos);
        }

        return null;
    }

    int findLeftmostKeywordPosition(IRegion lineInfo, String line, RobotLine rfeLine) {
        int startPos = 0;
        if (!rfeLine.arguments.isEmpty()) {
            startPos = rfeLine.arguments.get(0).getArgEndCharPos() - lineInfo.getOffset();
        }
        startPos = RobotWhitespace.skipMinimumRobotWhitespace(line, startPos);
        return startPos;
    }

    int findRightmostKeywordPosition(IRegion lineInfo, String line, RobotLine rfeLine) {
        int endPos = line.length();
        if (rfeLine.arguments.size() >= 3) {
            endPos = rfeLine.arguments.get(1).getArgEndCharPos() - lineInfo.getOffset();
        }
        return endPos;
    }

    private ICompletionProposal[] computeKeywordCompletionProposals(ITextViewer viewer, IDocument document, int documentOffset, final RobotLine rfeLine, final int leftPos, final int rightPos, int replacePos) {
        final ParsedString arg1 = rfeLine.arguments.size() >= 2 ? rfeLine.arguments.get(1) : null;
        IFile file = ResourceManager.resolveFileFor(document);
        final List<RobotCompletionProposal> proposals = new ArrayList<RobotCompletionProposal>();
        // first find matches that use the whole input as search string
        DefinitionFinder.acceptMatches(file, LineType.KEYWORD_TABLE_KEYWORD_BEGIN, new KeywordCompletionMatchVisitor(file, arg1, leftPos, proposals, rightPos, rfeLine, replacePos));
        if (arg1 != null && (proposals.isEmpty() || proposalsContainOnly(proposals, arg1))) {
            proposals.clear();
            int lineOffset = documentOffset - rfeLine.lineCharPos;
            if (leftPos < lineOffset && lineOffset < rightPos) {
                // try again, but only up to cursor
                int argumentOff = lineOffset - leftPos;
                ParsedString arg1leftPart = new ParsedString(arg1.getValue().substring(0, argumentOff), arg1.getArgCharPos());
                DefinitionFinder.acceptMatches(file, LineType.KEYWORD_TABLE_KEYWORD_BEGIN, new KeywordCompletionMatchVisitor(file, arg1leftPart, leftPos, proposals, rightPos, rfeLine, replacePos));
            }
            if (proposals.isEmpty() || proposalsContainOnly(proposals, arg1)) {
                // try again, ignoring user input, i.e. show all possible
                // keywords
                proposals.clear();
                DefinitionFinder.acceptMatches(file, LineType.KEYWORD_TABLE_KEYWORD_BEGIN, new KeywordCompletionMatchVisitor(file, null, leftPos, proposals, rightPos, rfeLine, replacePos));
            }
        }
        ICompletionProposal[] proposalsArr = new ICompletionProposal[proposals.size()];
        proposals.toArray(proposalsArr);
        return proposalsArr;
    }

    private boolean proposalsContainOnly(List<RobotCompletionProposal> proposals, ParsedString arg1) {
        if (proposals.size() != 1) {
            return false;
        }
        return proposals.get(0).getMatchKeyword().getValue().equals(arg1.getValue());
    }

    // ctrl-shift-space information popups
    @Override
    public IContextInformation[] computeContextInformation(ITextViewer viewer, int documentOffset) {
        // TODO replace with real implementation
        IContextInformation[] result = new IContextInformation[5];
        for (int i = 0; i < result.length; i++) {
            String contextDisplayString = "contextDisplayString " + i;
            String informationDisplayString = "informationDisplayString " + i;
            result[i] = new ContextInformation(contextDisplayString, informationDisplayString);
        }
        return result;
    }

    @Override
    public IContextInformationValidator getContextInformationValidator() {
        return new IContextInformationValidator() {

            private IContextInformation info;
            private ITextViewer viewer;
            private int offset;

            @Override
            public void install(IContextInformation info, ITextViewer viewer, int offset) {
                this.info = info;
                this.viewer = viewer;
                this.offset = offset;
            }

            @Override
            public boolean isContextInformationValid(int offset) {
                // TODO return false when cursor goes out of context for the
                // IContextInformation given to install()
                // see ContextInformationValidator.isContextInformationValid()

                // the user can always close a shown IContextInformation
                // instance by hitting Esc or moving the focus out of eclipse

                // if the previous IContextInformation is not closed before the
                // next is shown, it is temporarily hidden until the next one is
                // closed. This might confuse the user.
                return true;
            }

        };
    }

    @Override
    public char[] getCompletionProposalAutoActivationCharacters() {
        // TODO perhaps '$' or '{'? test it to see how it works..
        return null;
    }

    @Override
    public char[] getContextInformationAutoActivationCharacters() {
        return null;
    }

    @Override
    public String getErrorMessage() {
        return null;
    }

}
