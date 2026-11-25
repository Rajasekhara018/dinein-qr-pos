package com.heuristiq.documents.documenttype.support;

import com.heuristiq.documents.documenttype.application.ChangeRequestGateway;
import com.heuristiq.documents.documenttype.domain.DocumentTypeException;

import java.util.ArrayList;
import java.util.List;

/** Records submissions; can be switched to behave like an unreachable approval service. */
public class FakeChangeRequestGateway implements ChangeRequestGateway {

    public final List<Submission> submissions = new ArrayList<>();
    public final List<String> tokens = new ArrayList<>();
    public boolean down;

    @Override
    public SubmittedChangeRequest submit(Submission submission, String bearerToken) {
        if (down) {
            throw DocumentTypeException.approvalUnavailable("The approval service is unavailable. Nothing was changed.");
        }
        submissions.add(submission);
        tokens.add(bearerToken);
        return new SubmittedChangeRequest("cr-" + submissions.size(), "PENDING");
    }
}
