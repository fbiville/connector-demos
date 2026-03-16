CREATE CONSTRAINT comment_id_key FOR (c:Comment) REQUIRE c.id IS KEY;
CREATE CONSTRAINT person_id_key FOR (p:Person) REQUIRE p.id IS KEY;
