-- P5: taxonomy is a user's subjective choice, never invented catalog facts.
INSERT INTO taste_tags(label) VALUES ('Цитрусовый'),('Хвойный'),('Фруктовый'),('Цветочный'),
 ('Солодовый'),('Карамельный'),('Кофейный'),('Шоколадный'),('Дымный'),('Кислый'),('Горький'),('Пряный')
 ON CONFLICT(label) DO NOTHING;
CREATE INDEX checkins_occurred ON checkins(user_id,occurred_at DESC,id DESC) WHERE deleted_at IS NULL AND hidden_at IS NULL;
CREATE INDEX checkins_rating ON checkins(user_id,(coalesce(rating_quarters,-1)) DESC,id DESC) WHERE deleted_at IS NULL AND hidden_at IS NULL;
CREATE INDEX checkins_user_position ON checkins(user_id,position_id) WHERE deleted_at IS NULL AND hidden_at IS NULL;
CREATE INDEX checkins_body_trgm ON checkins USING gin (catalog_normalize(body) gin_trgm_ops) WHERE deleted_at IS NULL AND hidden_at IS NULL;
CREATE INDEX checkins_style_tag ON checkin_tags(tag_id,checkin_id);
