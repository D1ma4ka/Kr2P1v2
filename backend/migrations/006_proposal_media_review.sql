-- Bound moderator review to explicitly submitted pending catalog images.
CREATE INDEX proposals_pending_image ON position_proposals((proposed_data->>'imageAssetId'))
 WHERE status='pending' AND proposed_data->>'imageAssetId' IS NOT NULL;
