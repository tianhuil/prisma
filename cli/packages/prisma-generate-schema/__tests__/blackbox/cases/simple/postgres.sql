--
-- PostgreSQL database dump
--

-- Dumped from database version 10.1
-- Dumped by pg_dump version 11.1

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: schema-generator$simple; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA "schema-generator$simple";


SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: User; Type: TABLE; Schema: schema-generator$simple; Owner: -
--

CREATE TABLE "schema-generator$simple"."User" (
    id character varying(25) NOT NULL,
    name text NOT NULL,
    "updatedAt" timestamp(3) without time zone NOT NULL,
    "createdAt" timestamp(3) without time zone NOT NULL
);


--
-- Name: _RelayId; Type: TABLE; Schema: schema-generator$simple; Owner: -
--

CREATE TABLE "schema-generator$simple"."_RelayId" (
    id character varying(36) NOT NULL,
    "stableModelIdentifier" character varying(25) NOT NULL
);


--
-- Name: User User_pkey; Type: CONSTRAINT; Schema: schema-generator$simple; Owner: -
--

ALTER TABLE ONLY "schema-generator$simple"."User"
    ADD CONSTRAINT "User_pkey" PRIMARY KEY (id);


--
-- Name: _RelayId pk_RelayId; Type: CONSTRAINT; Schema: schema-generator$simple; Owner: -
--

ALTER TABLE ONLY "schema-generator$simple"."_RelayId"
    ADD CONSTRAINT "pk_RelayId" PRIMARY KEY (id);


--
-- PostgreSQL database dump complete
--

