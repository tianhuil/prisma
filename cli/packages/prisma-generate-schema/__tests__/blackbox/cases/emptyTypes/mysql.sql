-- MySQL dump 10.17  Distrib 10.3.12-MariaDB, for Linux (x86_64)
--
-- Host: localhost    Database: schema-generator@emptyTypes
-- ------------------------------------------------------
-- Server version	5.7.23


--
-- Table structure for table `OnlyDate`
--

DROP TABLE IF EXISTS `OnlyDate`;
CREATE TABLE `OnlyDate` (
  `id` char(25) CHARACTER SET utf8 NOT NULL,
  `createdAt` datetime(3) NOT NULL,
  `updatedAt` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `id_UNIQUE` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `OnlyId`
--

DROP TABLE IF EXISTS `OnlyId`;
CREATE TABLE `OnlyId` (
  `id` char(25) CHARACTER SET utf8 NOT NULL,
  `updatedAt` datetime(3) NOT NULL,
  `createdAt` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `id_UNIQUE` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `OnlyIdAndARelation`
--

DROP TABLE IF EXISTS `OnlyIdAndARelation`;
CREATE TABLE `OnlyIdAndARelation` (
  `id` char(25) CHARACTER SET utf8 NOT NULL,
  `updatedAt` datetime(3) NOT NULL,
  `createdAt` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `id_UNIQUE` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `OnlyIdAndARelation2`
--

DROP TABLE IF EXISTS `OnlyIdAndARelation2`;
CREATE TABLE `OnlyIdAndARelation2` (
  `id` char(25) CHARACTER SET utf8 NOT NULL,
  `updatedAt` datetime(3) NOT NULL,
  `createdAt` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `id_UNIQUE` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `OnlyRelation`
--

DROP TABLE IF EXISTS `OnlyRelation`;
CREATE TABLE `OnlyRelation` (
  `id` char(25) CHARACTER SET utf8 NOT NULL,
  `updatedAt` datetime(3) NOT NULL,
  `createdAt` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `id_UNIQUE` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `OnlyRelationA`
--

DROP TABLE IF EXISTS `OnlyRelationA`;
CREATE TABLE `OnlyRelationA` (
  `id` char(25) CHARACTER SET utf8 NOT NULL,
  `updatedAt` datetime(3) NOT NULL,
  `createdAt` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `id_UNIQUE` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `OnlyRelationB`
--

DROP TABLE IF EXISTS `OnlyRelationB`;
CREATE TABLE `OnlyRelationB` (
  `id` char(25) CHARACTER SET utf8 NOT NULL,
  `updatedAt` datetime(3) NOT NULL,
  `createdAt` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `id_UNIQUE` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `_OnlyDateToOnlyRelation`
--

DROP TABLE IF EXISTS `_OnlyDateToOnlyRelation`;
CREATE TABLE `_OnlyDateToOnlyRelation` (
  `A` char(25) CHARACTER SET utf8 NOT NULL,
  `B` char(25) CHARACTER SET utf8 NOT NULL,
  UNIQUE KEY `AB_unique` (`A`,`B`),
  KEY `A` (`A`),
  KEY `B` (`B`),
  CONSTRAINT `_OnlyDateToOnlyRelation_ibfk_1` FOREIGN KEY (`A`) REFERENCES `OnlyDate` (`id`) ON DELETE CASCADE,
  CONSTRAINT `_OnlyDateToOnlyRelation_ibfk_2` FOREIGN KEY (`B`) REFERENCES `OnlyRelation` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `_OnlyIdToOnlyIdAndARelation`
--

DROP TABLE IF EXISTS `_OnlyIdToOnlyIdAndARelation`;
CREATE TABLE `_OnlyIdToOnlyIdAndARelation` (
  `A` char(25) CHARACTER SET utf8 NOT NULL,
  `B` char(25) CHARACTER SET utf8 NOT NULL,
  UNIQUE KEY `AB_unique` (`A`,`B`),
  KEY `A` (`A`),
  KEY `B` (`B`),
  CONSTRAINT `_OnlyIdToOnlyIdAndARelation_ibfk_1` FOREIGN KEY (`A`) REFERENCES `OnlyId` (`id`) ON DELETE CASCADE,
  CONSTRAINT `_OnlyIdToOnlyIdAndARelation_ibfk_2` FOREIGN KEY (`B`) REFERENCES `OnlyIdAndARelation` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `_OnlyIdToOnlyIdAndARelation2`
--

DROP TABLE IF EXISTS `_OnlyIdToOnlyIdAndARelation2`;
CREATE TABLE `_OnlyIdToOnlyIdAndARelation2` (
  `A` char(25) CHARACTER SET utf8 NOT NULL,
  `B` char(25) CHARACTER SET utf8 NOT NULL,
  UNIQUE KEY `AB_unique` (`A`,`B`),
  KEY `A` (`A`),
  KEY `B` (`B`),
  CONSTRAINT `_OnlyIdToOnlyIdAndARelation2_ibfk_1` FOREIGN KEY (`A`) REFERENCES `OnlyId` (`id`) ON DELETE CASCADE,
  CONSTRAINT `_OnlyIdToOnlyIdAndARelation2_ibfk_2` FOREIGN KEY (`B`) REFERENCES `OnlyIdAndARelation2` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `_OnlyRelationAToOnlyRelationA`
--

DROP TABLE IF EXISTS `_OnlyRelationAToOnlyRelationA`;
CREATE TABLE `_OnlyRelationAToOnlyRelationA` (
  `A` char(25) CHARACTER SET utf8 NOT NULL,
  `B` char(25) CHARACTER SET utf8 NOT NULL,
  UNIQUE KEY `AB_unique` (`A`,`B`),
  KEY `A` (`A`),
  KEY `B` (`B`),
  CONSTRAINT `_OnlyRelationAToOnlyRelationA_ibfk_1` FOREIGN KEY (`A`) REFERENCES `OnlyRelationA` (`id`) ON DELETE CASCADE,
  CONSTRAINT `_OnlyRelationAToOnlyRelationA_ibfk_2` FOREIGN KEY (`B`) REFERENCES `OnlyRelationA` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `_OnlyRelationBToOnlyRelationB`
--

DROP TABLE IF EXISTS `_OnlyRelationBToOnlyRelationB`;
CREATE TABLE `_OnlyRelationBToOnlyRelationB` (
  `A` char(25) CHARACTER SET utf8 NOT NULL,
  `B` char(25) CHARACTER SET utf8 NOT NULL,
  UNIQUE KEY `AB_unique` (`A`,`B`),
  KEY `A` (`A`),
  KEY `B` (`B`),
  CONSTRAINT `_OnlyRelationBToOnlyRelationB_ibfk_1` FOREIGN KEY (`A`) REFERENCES `OnlyRelationB` (`id`) ON DELETE CASCADE,
  CONSTRAINT `_OnlyRelationBToOnlyRelationB_ibfk_2` FOREIGN KEY (`B`) REFERENCES `OnlyRelationB` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `_RelayId`
--

DROP TABLE IF EXISTS `_RelayId`;
CREATE TABLE `_RelayId` (
  `id` char(25) CHARACTER SET utf8 NOT NULL,
  `stableModelIdentifier` char(25) CHARACTER SET utf8 NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `id_UNIQUE` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Dump completed on 2019-04-29 12:50:23
