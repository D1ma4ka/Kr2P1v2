CREATE TABLE kra_pet_definitions (
 id text PRIMARY KEY CHECK (id ~ '^[a-z][a-z0-9_]{2,63}$'),
 name text NOT NULL,
 personality text NOT NULL,
 rig_version integer NOT NULL DEFAULT 1 CHECK (rig_version > 0),
 rarity text NOT NULL DEFAULT 'common' CHECK (rarity IN ('common','uncommon','rare','epic','legendary','exclusive')),
 starter boolean NOT NULL DEFAULT false,
 enabled boolean NOT NULL DEFAULT true
);
INSERT INTO kra_pet_definitions(id,name,personality,starter) VALUES
 ('starter_hop','Хмель','Весёлый и энергичный',true),
 ('starter_cap','Крышечка','Самоуверенная и харизматичная',true),
 ('starter_bottle','Бутылочка','Спокойная и дружелюбная',true),
 ('starter_nettle','Крапивка','Любопытная и озорная',true),
 ('starter_tap','Краник','Общительный и забавный',true);

CREATE TABLE kra_pets (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 definition_id text NOT NULL REFERENCES kra_pet_definitions(id),
 name text NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 32),
 origin text NOT NULL,
 acquired_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(user_id,id)
);
CREATE UNIQUE INDEX kra_pets_one_starter ON kra_pets(user_id) WHERE origin='starter';

CREATE TABLE kra_economy (
 version integer PRIMARY KEY CHECK (version > 0),
 reward integer NOT NULL CHECK (reward BETWEEN 0 AND 100),
 opening_balance integer NOT NULL CHECK (opening_balance BETWEEN 0 AND 10000),
 daily_limit integer CHECK (daily_limit BETWEEN 1 AND 10000),
 removal_policy text NOT NULL CHECK (removal_policy IN ('retain','recover_available')),
 created_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO kra_economy VALUES (1,2,0,NULL,'retain');

CREATE TABLE kra_wallets (
 user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
 balance integer NOT NULL DEFAULT 0 CHECK (balance >= 0)
);
CREATE TABLE kra_wallet_entries (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 user_id uuid NOT NULL REFERENCES kra_wallets(user_id) ON DELETE CASCADE,
 reason text NOT NULL CHECK (reason IN ('starter','checkin','purchase','checkin_removal','admin')),
 event_id uuid NOT NULL,
 amount integer NOT NULL,
 config_version integer NOT NULL REFERENCES kra_economy(version),
 actor_id uuid REFERENCES users(id) ON DELETE SET NULL,
 note text,
 created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(user_id,reason,event_id)
);
CREATE INDEX kra_wallet_history ON kra_wallet_entries(user_id,id DESC);
CREATE INDEX kra_wallet_checkin_daily ON kra_wallet_entries(user_id,created_at) WHERE reason='checkin';

CREATE TABLE kra_rooms (
 user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
 active_pet_id uuid NOT NULL,
 FOREIGN KEY(user_id,active_pet_id) REFERENCES kra_pets(user_id,id) ON DELETE CASCADE
);

CREATE TABLE kra_item_definitions (
 id text PRIMARY KEY CHECK (id ~ '^[a-z][a-z0-9_]{2,63}$'),
 name text NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 80),
 kind text NOT NULL CHECK (kind IN ('wear','food','decor')),
 category text NOT NULL,
 rarity text NOT NULL CHECK (rarity IN ('common','uncommon','rare','epic','legendary','exclusive')),
 price integer CHECK (price BETWEEN 0 AND 100000),
 slots text[] NOT NULL DEFAULT '{}',
 compatible_pets text[] NOT NULL DEFAULT '{}',
 render jsonb NOT NULL CHECK (jsonb_typeof(render)='object'),
 asset_version integer NOT NULL DEFAULT 1 CHECK (asset_version > 0),
 enabled boolean NOT NULL DEFAULT true,
 transferable boolean NOT NULL DEFAULT false,
 series text,
 partner_id text,
 available_from timestamptz,
 available_until timestamptz,
 CHECK (available_until IS NULL OR available_from IS NULL OR available_until>available_from)
);
CREATE TABLE kra_items (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 definition_id text NOT NULL REFERENCES kra_item_definitions(id),
 source text NOT NULL CHECK (source IN ('purchase','partner')),
 created_at timestamptz NOT NULL DEFAULT now(),
 consumed_at timestamptz,
 UNIQUE(user_id,id)
);
CREATE INDEX kra_item_inventory ON kra_items(user_id,created_at) WHERE consumed_at IS NULL;
CREATE TABLE kra_ownership (
 item_id uuid NOT NULL REFERENCES kra_items(id) ON DELETE CASCADE,
 user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 source text NOT NULL,
 acquired_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(item_id,acquired_at)
);
CREATE TABLE kra_actions (
 user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 request_id uuid NOT NULL,
 operation text NOT NULL CHECK (operation IN ('purchase','consume')),
 subject text NOT NULL,
 item_id uuid NOT NULL REFERENCES kra_items(id) ON DELETE CASCADE,
 PRIMARY KEY(user_id,request_id)
);
CREATE TABLE kra_equipped (
 user_id uuid NOT NULL,
 pet_id uuid NOT NULL,
 slot text NOT NULL,
 item_id uuid NOT NULL,
 PRIMARY KEY(pet_id,slot),
 UNIQUE(item_id),
 FOREIGN KEY(user_id,pet_id) REFERENCES kra_pets(user_id,id) ON DELETE CASCADE,
 FOREIGN KEY(user_id,item_id) REFERENCES kra_items(user_id,id) ON DELETE CASCADE
);
CREATE TABLE kra_room_decor (
 user_id uuid NOT NULL REFERENCES kra_rooms(user_id) ON DELETE CASCADE,
 slot text NOT NULL,
 item_id uuid NOT NULL,
 PRIMARY KEY(user_id,slot),
 UNIQUE(item_id),
 FOREIGN KEY(user_id,item_id) REFERENCES kra_items(user_id,id) ON DELETE CASCADE
);

CREATE TABLE kra_partner_rewards (
 id text PRIMARY KEY,
 partner_id text NOT NULL,
 event_id text NOT NULL,
 campaign_id text NOT NULL,
 item_id text NOT NULL REFERENCES kra_item_definitions(id),
 code_hash char(64) UNIQUE NOT NULL,
 starts_at timestamptz NOT NULL,
 ends_at timestamptz NOT NULL CHECK (ends_at>starts_at),
 supply integer NOT NULL CHECK (supply>0),
 test_only boolean NOT NULL DEFAULT true,
 enabled boolean NOT NULL DEFAULT false
);
CREATE TABLE kra_redemptions (
 reward_id text NOT NULL REFERENCES kra_partner_rewards(id),
 user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 item_id uuid NOT NULL REFERENCES kra_items(id) ON DELETE CASCADE,
 redeemed_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(reward_id,user_id)
);

CREATE FUNCTION kra_recover_checkin_credit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE policy kra_economy%ROWTYPE;
 credit integer;
 available integer;
 recovered integer;
BEGIN
 IF (NEW.deleted_at IS NULL AND NEW.hidden_at IS NULL)
    OR (OLD.deleted_at IS NOT NULL OR OLD.hidden_at IS NOT NULL) THEN RETURN NEW; END IF;
 SELECT * INTO policy FROM kra_economy ORDER BY version DESC LIMIT 1;
 IF policy.removal_policy = 'retain' THEN RETURN NEW; END IF;
 SELECT amount INTO credit FROM kra_wallet_entries
  WHERE user_id = NEW.user_id AND reason = 'checkin' AND event_id = NEW.id;
 IF NOT FOUND THEN RETURN NEW; END IF;
 SELECT balance INTO available FROM kra_wallets WHERE user_id=NEW.user_id FOR UPDATE;
 recovered := least(credit,available);
 INSERT INTO kra_wallet_entries(user_id,reason,event_id,amount,config_version,note)
 VALUES (NEW.user_id,'checkin_removal',NEW.id,-recovered,policy.version,
   CASE WHEN recovered < credit THEN 'partial: balance already spent' ELSE NULL END)
 ON CONFLICT(user_id,reason,event_id) DO NOTHING;
 IF FOUND THEN UPDATE kra_wallets SET balance=balance-recovered WHERE user_id=NEW.user_id; END IF;
 RETURN NEW;
END
$$;
CREATE TRIGGER kra_checkin_removed AFTER UPDATE OF hidden_at, deleted_at ON checkins
FOR EACH ROW EXECUTE FUNCTION kra_recover_checkin_credit();

DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='krapiva_app') THEN
  EXECUTE 'GRANT SELECT ON kra_pet_definitions,kra_economy,kra_item_definitions,kra_partner_rewards TO krapiva_app';
  EXECUTE 'GRANT SELECT,INSERT,UPDATE,DELETE ON kra_pets,kra_rooms,kra_wallets,kra_items,kra_equipped,kra_room_decor TO krapiva_app';
  EXECUTE 'GRANT SELECT,INSERT ON kra_wallet_entries,kra_ownership,kra_actions,kra_redemptions TO krapiva_app';
  EXECUTE 'GRANT USAGE,SELECT ON SEQUENCE kra_wallet_entries_id_seq TO krapiva_app';
 END IF;
END $$;
