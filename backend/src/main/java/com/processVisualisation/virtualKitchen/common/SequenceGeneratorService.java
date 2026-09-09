package com.processVisualisation.virtualKitchen.common;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static org.springframework.data.mongodb.core.FindAndModifyOptions.options;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Generates sequential (auto-increment style) numeric ids backed by
 * {@link DBSequence} counter documents in MongoDB, used wherever the
 * application needs simple incrementing ids instead of Mongo's default
 * ObjectId.
 */
@Service
public class SequenceGeneratorService {

    @Autowired
    private MongoOperations mongoOperations;

    /**
     * Atomically increments (creating it with an initial value if it doesn't
     * exist) the named counter document and returns its new value, via a
     * MongoDB find-and-modify upsert so concurrent callers never receive the
     * same value.
     *
     * @param seqName id of the counter document to increment (e.g. {@code "users_sequence"})
     * @return the next value in the named sequence, or {@code 1} if the
     *         find-and-modify unexpectedly returned no document
     */
    public long generateSequence(String seqName) {
        DBSequence counter = mongoOperations.findAndModify(
                query(where("_id").is(seqName)),
                new Update().inc("seq", 1),
                options().returnNew(true).upsert(true),
                DBSequence.class);
        return !Objects.isNull(counter) ? counter.getSeq() : 1;
    }
}
