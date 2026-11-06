package dev.jagadeesh.shortlink.service;

import dev.jagadeesh.shortlink.domain.Counter;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.math.BigInteger;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Generates 7-character codes that never collide and do not look sequential.
 *
 * <p>A MongoDB counter hands out 1, 2, 3, ... with one atomic {@code findAndModify($inc)}, which is safe
 * across any number of app instances. Each number is then mapped through {@code n * MULTIPLIER mod 62^7}.
 * Because MULTIPLIER is coprime with 62^7 the mapping is a bijection: distinct numbers always give distinct
 * codes, but neighbouring numbers give unrelated-looking codes, so links cannot be enumerated by counting.
 */
@Component
public class CodeGenerator {

    static final int LENGTH = 7;
    static final long SPACE = 3_521_614_606_208L;        // 62^7
    static final long MULTIPLIER = 1_580_030_173L;       // prime, not a factor of 62^7 (= 2^7 * 31^7)

    private final MongoTemplate mongo;

    public CodeGenerator(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public String next() {
        Counter c = mongo.findAndModify(
                Query.query(where("_id").is("links")),
                new Update().inc("seq", 1),
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                Counter.class);
        return scramble(c.seq());
    }

    static String scramble(long n) {
        // n * MULTIPLIER can exceed a long, so multiply in BigInteger and reduce mod 62^7
        long mixed = BigInteger.valueOf(n).multiply(BIG_MULTIPLIER).mod(BIG_SPACE).longValue();
        return Base62.encode(mixed, LENGTH);
    }

    private static final BigInteger BIG_MULTIPLIER = BigInteger.valueOf(MULTIPLIER);
    private static final BigInteger BIG_SPACE = BigInteger.valueOf(SPACE);
}
