-- phpMyAdmin SQL Dump
-- version 4.6.6
-- https://www.phpmyadmin.net/
--
-- Máy chủ: localhost
-- Thời gian đã tạo: Th6 09, 2025 lúc 02:18 SA
-- Phiên bản máy phục vụ: 5.7.17-log
-- Phiên bản PHP: 5.6.30

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Cơ sở dữ liệu: `chatapp`
--

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `conversations`
--

CREATE TABLE `conversations` (
  `id` bigint(20) NOT NULL,
  `title` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` enum('DIRECT','GROUP') COLLATE utf8mb4_unicode_ci NOT NULL,
  `participant1_id` bigint(20) NOT NULL,
  `participant2_id` bigint(20) DEFAULT NULL,
  `is_archived` tinyint(1) DEFAULT '0',
  `is_pinned` tinyint(1) DEFAULT '0',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `last_message_at` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Đang đổ dữ liệu cho bảng `conversations`
--

INSERT INTO `conversations` (`id`, `title`, `type`, `participant1_id`, `participant2_id`, `is_archived`, `is_pinned`, `created_at`, `updated_at`, `last_message_at`) VALUES
(1, NULL, 'DIRECT', 1, 2, 0, 0, '2025-06-02 07:35:59', '2025-06-04 21:14:23', '2025-06-04 21:14:23'),
(2, NULL, 'DIRECT', 1, 3, 0, 0, '2025-06-02 07:35:59', '2025-06-06 02:33:03', '2025-06-06 02:33:03'),
(3, NULL, 'DIRECT', 4, 1, 0, 0, '2025-06-02 03:06:40', '2025-06-05 01:17:04', '2025-06-05 01:17:04');

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `file_attachments`
--

CREATE TABLE `file_attachments` (
  `id` bigint(20) NOT NULL,
  `message_id` bigint(20) NOT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `original_file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_path` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_type` enum('IMAGE','VOICE','DOCUMENT','VIDEO','OTHER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_size` bigint(20) NOT NULL,
  `mime_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `thumbnail_path` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `uploaded_by` bigint(20) NOT NULL,
  `uploaded_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `messages`
--

CREATE TABLE `messages` (
  `id` bigint(20) NOT NULL,
  `message_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `room_id` bigint(20) DEFAULT NULL,
  `conversation_id` bigint(20) DEFAULT NULL,
  `sender_id` bigint(20) NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `encrypted_content` text COLLATE utf8mb4_unicode_ci,
  `type` enum('CHAT','JOIN','LEAVE','FILE','VOICE','TYPING','STOP_TYPING') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('SENDING','SENT','DELIVERED','READ','FAILED') COLLATE utf8mb4_unicode_ci DEFAULT 'SENT',
  `reply_to_id` bigint(20) DEFAULT NULL,
  `is_deleted` tinyint(1) DEFAULT '0',
  `is_edited` tinyint(1) DEFAULT '0',
  `deleted_at` timestamp NULL DEFAULT NULL,
  `deleted_by` bigint(20) DEFAULT NULL,
  `edited_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_pinned` tinyint(1) NOT NULL DEFAULT '0',
  `pinned_at` timestamp NULL DEFAULT NULL COMMENT 'When the message was pinned',
  `pinned_by` bigint(20) DEFAULT NULL COMMENT 'User ID who pinned the message'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Đang đổ dữ liệu cho bảng `messages`
--

INSERT INTO `messages` (`id`, `message_id`, `room_id`, `conversation_id`, `sender_id`, `content`, `encrypted_content`, `type`, `status`, `reply_to_id`, `is_deleted`, `is_edited`, `deleted_at`, `deleted_by`, `edited_at`, `created_at`, `updated_at`, `is_pinned`, `pinned_at`, `pinned_by`) VALUES
(1, '45ba0abb-3f84-11f0-abdc-0433c2abf6d4', 1, NULL, 1, 'Chào mừng các bạn đến với phòng chat chung!', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-02 07:35:59', '2025-06-06 00:00:19', 0, NULL, NULL),
(2, '45ba1f77-3f84-11f0-abdc-0433c2abf6d4', 1, NULL, 2, 'Xin chào mọi người!', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-02 07:35:59', '2025-06-06 02:48:58', 0, NULL, NULL),
(3, '45bd6cac-3f84-11f0-abdc-0433c2abf6d4', NULL, 1, 1, 'Chào bạn! Bạn có khỏe không?', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-02 07:36:00', '2025-06-06 02:48:58', 0, NULL, NULL),
(4, '45bd83c1-3f84-11f0-abdc-0433c2abf6d4', NULL, 1, 2, 'Tôi khỏe, cảm ơn bạn!', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-02 07:36:00', '2025-06-06 02:48:58', 0, NULL, NULL),
(5, 'aab4ef78-75f6-4e18-b10d-17dc24e05ec7', NULL, 1, 2, 'hello', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-02 00:40:43', '2025-06-06 02:48:58', 0, NULL, NULL),
(6, 'a39950d8-2810-4e44-85e7-8fc6504ff315', NULL, 1, 1, 'uk', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-02 00:41:28', '2025-06-06 02:48:58', 0, NULL, NULL),
(7, '52fcf0d2-c5fd-453d-a272-c02953ad35ff', NULL, 1, 2, '\"1\"', NULL, 'CHAT', 'READ', NULL, 0, 1, NULL, NULL, '2025-06-02 01:12:09', '2025-06-02 00:57:42', '2025-06-06 02:48:58', 0, NULL, NULL),
(8, '81a5d67d-0f6f-408a-86c3-9cc53a82131e', NULL, 1, 2, '123123', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-02 01:12:22', '2025-06-06 02:48:58', 0, NULL, NULL),
(9, '498ae16e-bd54-4a5e-b5e9-8bd8ba53eebd', NULL, 2, 3, 'ok', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-02 01:47:23', '2025-06-05 19:50:29', 0, NULL, NULL),
(10, '3c97df02-713f-4be7-b580-eae32c592992', NULL, 2, 1, 'uk', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-02 01:48:29', '2025-06-06 02:48:58', 0, NULL, NULL),
(11, '4160824a-ced4-4e8f-b626-016c8784a7db', NULL, 2, 1, 'humm', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-02 02:18:07', '2025-06-06 02:48:58', 0, NULL, NULL),
(12, '89b5d163-a7a0-4fea-b0f1-a56be2573b8a', NULL, 1, 1, 'ok', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-02 02:22:15', '2025-06-06 02:48:58', 0, NULL, NULL),
(13, '86243078-05bf-475e-a1fe-02468ca978fb', NULL, 3, 1, 'xin chào bạn có khỏe không?', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-02 03:09:09', '2025-06-06 02:48:58', 0, NULL, NULL),
(14, 'd16b939f-6475-44f3-ba69-fa434dd17132', NULL, 3, 4, '\"I\'m fine thanks\"', NULL, 'CHAT', 'READ', NULL, 0, 1, NULL, NULL, '2025-06-02 03:12:58', '2025-06-02 03:11:38', '2025-06-06 03:38:23', 0, NULL, NULL),
(15, '24885d4c-9586-4b21-908d-7473305abc7e', NULL, 3, 4, 'hì hì', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-02 03:14:02', '2025-06-06 01:22:17', 0, NULL, NULL),
(16, '3fa0d6ec-a261-4558-acf3-fca01236a67b', 1, NULL, 1, 'ukm', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-04 20:13:00', '2025-06-05 19:53:28', 0, NULL, NULL),
(17, '724b112c-d308-44b2-bc26-84e01078d21d', NULL, 2, 1, '1231', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-04 20:20:31', '2025-06-06 02:48:58', 0, NULL, NULL),
(18, '068489ce-1ba2-4bc9-9b6f-63d6b86252b0', 7, NULL, 1, 'xin chào', NULL, 'CHAT', 'SENT', NULL, 0, 0, NULL, NULL, NULL, '2025-06-04 20:56:59', '2025-06-06 02:48:58', 0, NULL, NULL),
(19, 'ec066b4b-0c26-4753-8a98-c47dfa0841da', NULL, 1, 1, 'uk', NULL, 'CHAT', 'SENT', NULL, 0, 0, NULL, NULL, NULL, '2025-06-04 21:14:23', '2025-06-06 02:48:58', 0, NULL, NULL),
(20, '61f5e625-f030-4716-9852-0dd88c8969a7', 8, NULL, 1, 'hello', NULL, 'CHAT', 'SENT', NULL, 0, 0, NULL, NULL, NULL, '2025-06-04 21:31:41', '2025-06-06 02:48:58', 0, NULL, NULL),
(21, '23b96c61-43b1-46ad-86cf-fc996bbd6d1f', 8, NULL, 1, 'hello', NULL, 'CHAT', 'SENT', NULL, 0, 0, NULL, NULL, NULL, '2025-06-04 21:31:56', '2025-06-06 02:48:58', 0, NULL, NULL),
(22, 'dc50f85e-393e-4b2b-92a0-f53c173b872d', NULL, 2, 1, 'adsad', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 00:51:31', '2025-06-06 02:48:58', 0, NULL, NULL),
(23, '47e9ed9d-66fc-4578-872b-67a81201211d', NULL, 3, 1, '123213', NULL, 'CHAT', 'SENT', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 01:17:04', '2025-06-06 01:22:04', 0, NULL, NULL),
(24, '778a07d4-df41-4c87-a8d9-cd26087c15f2', 1, NULL, 3, 'xin chào all ae công ty nhá hihihi', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 01:34:14', '2025-06-05 21:11:44', 0, NULL, NULL),
(25, 'cfbb401e-396b-42ae-8592-875332e4d197', 1, NULL, 1, 'ukm hay đấy', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 01:39:00', '2025-06-06 02:48:58', 0, NULL, NULL),
(26, 'b8c2c34b-86c8-480b-a8d9-23b78c2f4241', 1, NULL, 3, ':v', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 01:39:05', '2025-06-06 02:48:58', 0, NULL, NULL),
(27, 'd1a2ccb7-a8ed-42d9-aa80-1a5994b2c093', NULL, 2, 3, '123123123123213', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 02:23:45', '2025-06-05 19:50:33', 0, NULL, NULL),
(28, '8d65be0b-3aee-4136-a0df-a5fcea8820c9', NULL, 2, 1, 'uk', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 02:23:59', '2025-06-06 02:48:58', 0, NULL, NULL),
(29, 'b0c76bb8-010f-4ece-aa86-36dba461db5c', NULL, 2, 1, '123213', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 02:34:18', '2025-06-06 02:48:58', 0, NULL, NULL),
(30, '9d536ca6-8719-4aad-a9a5-386f20faa656', NULL, 2, 3, '123', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 02:34:23', '2025-06-05 19:50:36', 0, NULL, NULL),
(31, 'e4503b50-8a13-4765-b77e-8e1a7c08e199', NULL, 2, 1, '123213', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 02:48:46', '2025-06-06 01:22:43', 0, NULL, NULL),
(32, 'b09a4099-8bc5-4ae3-9d72-780873310d76', NULL, 2, 1, '123213213213', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 02:48:48', '2025-06-06 03:42:12', 0, NULL, NULL),
(33, '67106a51-ea87-4732-80b7-21af05d669dd', NULL, 2, 3, '123213213', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 02:48:51', '2025-06-05 19:50:39', 0, NULL, NULL),
(34, '18ecee51-f6b0-481a-b245-cd3ea296c23c', NULL, 2, 3, '123213', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 02:48:53', '2025-06-05 19:50:44', 0, NULL, NULL),
(35, '6741ae16-2ab4-4652-b3ad-21b236703060', 1, NULL, 3, 'hì hì', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 03:01:24', '2025-06-05 19:49:34', 0, NULL, NULL),
(36, '02ea9e2c-fef3-4ac5-82e4-9e4187905822', 1, NULL, 3, ':v', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 03:01:28', '2025-06-05 19:49:33', 0, NULL, NULL),
(37, '415b098a-3b45-44ac-990c-caaa3a915e71', 1, NULL, 1, 'kekeke', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 03:01:31', '2025-06-06 02:48:58', 0, NULL, NULL),
(38, '816da2d7-5479-49d4-9005-a3104e36b624', 1, NULL, 3, 'hix hix', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 19:52:48', '2025-06-06 00:00:32', 0, NULL, NULL),
(39, '651d099f-3135-41e3-915b-8be0b28cd869', NULL, 2, 3, '123213213', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 20:26:29', '2025-06-05 20:26:35', 0, NULL, NULL),
(40, 'e2be921c-9ca7-44e1-a318-303d5981047f', NULL, 2, 3, '3213', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 20:26:33', '2025-06-05 20:26:35', 0, NULL, NULL),
(41, '1453d0ff-5e19-431c-b999-aae01b81c98e', NULL, 2, 3, '123213213', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 20:27:41', '2025-06-05 20:27:43', 0, NULL, NULL),
(42, 'aa9b3b14-b6bf-4a69-841c-2f1efc1a30c5', NULL, 2, 3, '123213', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 20:27:54', '2025-06-06 02:35:37', 0, NULL, NULL),
(43, '05c81597-354e-4c5e-a13b-3cfb3322a377', NULL, 2, 1, 'xcxcx', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 20:28:07', '2025-06-06 01:22:39', 0, NULL, NULL),
(44, '233a2a9b-5e99-42c1-80e2-25981ba89c4f', NULL, 2, 3, 'cxcxc', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 20:35:22', '2025-06-05 20:35:23', 0, NULL, NULL),
(45, 'aa2b34ba-6713-4c1f-9959-a51d830c0bb9', NULL, 2, 1, '123123', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 20:35:26', '2025-06-05 20:35:27', 0, NULL, NULL),
(46, 'e1317ae8-3758-400f-8ce3-270e6a607a37', NULL, 2, 3, '123123', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 20:35:47', '2025-06-05 20:35:48', 0, NULL, NULL),
(47, 'c76f555e-ee38-443e-bff9-4a303eead375', NULL, 2, 1, '123123213', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 20:35:51', '2025-06-05 20:35:52', 0, NULL, NULL),
(48, '52e73c59-10c4-41a3-a584-d3471877ac47', NULL, 2, 1, '123123213', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 20:36:34', '2025-06-06 01:22:36', 0, NULL, NULL),
(49, '022255b2-4b43-4248-b155-acdb2b17b9a6', NULL, 2, 3, '123213213', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 20:36:37', '2025-06-05 20:36:38', 0, NULL, NULL),
(50, '6e2f45b3-2e76-4560-8a2a-7ed1929828ae', NULL, 2, 3, '123213', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 20:51:30', '2025-06-05 20:51:31', 0, NULL, NULL),
(51, '7bbde04d-2ba8-4119-b1ff-a7c38ada7232', NULL, 2, 1, '12321321', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 20:51:36', '2025-06-06 01:22:30', 0, NULL, NULL),
(52, '774b9d84-4040-4e3c-af15-c1438be4f8a0', NULL, 2, 1, '123', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 21:00:44', '2025-06-06 01:22:33', 0, NULL, NULL),
(53, 'fb77bc96-15f8-4259-88f9-2bff7d9536a8', NULL, 2, 3, '213', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 21:00:48', '2025-06-05 21:00:49', 0, NULL, NULL),
(54, '34d65b1a-2511-4cb9-8e91-0ac895fa10d6', NULL, 2, 3, '123', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 21:14:59', '2025-06-05 21:15:00', 0, NULL, NULL),
(55, '666fb239-0d17-444a-ba36-a70fe0dd4acf', NULL, 2, 1, '456', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 21:15:03', '2025-06-05 21:15:04', 0, NULL, NULL),
(56, '3e1072cd-feb0-49cb-9d25-5bb161cc3e4b', NULL, 2, 3, '123', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 21:18:28', '2025-06-05 21:18:29', 0, NULL, NULL),
(57, 'f95072e1-8045-43d8-82ca-7b4ac6b715d4', NULL, 2, 1, '3213', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 21:18:36', '2025-06-05 21:18:37', 0, NULL, NULL),
(58, 'edecedac-234b-47f1-8b84-7e5c87e6e747', NULL, 2, 3, '123', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 21:27:40', '2025-06-05 21:27:42', 0, NULL, NULL),
(59, '84b1b245-2b2c-413f-b278-a6d22e9d2fb2', NULL, 2, 1, '456', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 21:27:52', '2025-06-05 21:27:53', 0, NULL, NULL),
(60, '48194375-ae12-4713-9b29-1e5c54b62cf1', NULL, 2, 3, 'zcxzc', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 21:32:12', '2025-06-05 21:32:13', 0, NULL, NULL),
(61, 'd8dc2969-c40d-4034-b135-6657004c75f3', NULL, 2, 1, 'czxczxc', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 21:32:16', '2025-06-05 21:32:18', 0, NULL, NULL),
(62, 'a940c798-2de3-4893-94d0-7d8e0c4ad6cd', NULL, 2, 3, '123123', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 21:32:26', '2025-06-05 21:32:27', 0, NULL, NULL),
(63, '7f8af7b5-54ec-41f1-92a0-516a8cb5fc4d', NULL, 2, 3, '123', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 21:33:02', '2025-06-05 21:33:03', 0, NULL, NULL),
(64, '4bd6a63a-e60d-45ac-b51b-2c2f8b72caea', NULL, 2, 1, '123213', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-05 21:33:07', '2025-06-06 02:35:09', 0, NULL, NULL),
(65, '340e3fe6-fd49-4bfb-991f-47379c47e40e', NULL, 2, 1, '123', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-06 00:24:04', '2025-06-06 03:42:22', 0, NULL, NULL),
(66, '537a915c-52f1-4011-91ee-52f4199cfad4', NULL, 2, 3, '12312', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-06 00:24:08', '2025-06-06 00:24:09', 0, NULL, NULL),
(67, 'a4bb971b-4e54-47df-a925-7a12f7613057', NULL, 2, 3, '123', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-06 00:29:45', '2025-06-06 00:29:48', 0, NULL, NULL),
(68, 'de49a59d-b026-47a5-a650-94561c4e9b12', NULL, 2, 1, '1234', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-06 00:29:57', '2025-06-06 00:29:59', 0, NULL, NULL),
(69, '8f62f9e2-5d5a-457c-ad23-d7cb65e53b44', NULL, 2, 1, '123213', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-06 00:34:32', '2025-06-06 03:42:28', 1, '2025-06-06 03:42:28', 3),
(70, '218d35d7-3a08-47a9-acae-4fbacb00b5b5', NULL, 2, 3, '123', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-06 00:34:40', '2025-06-06 00:34:41', 0, NULL, NULL),
(71, '8e721989-0ed5-4b06-8224-ea8b42d9d22a', NULL, 2, 3, '123', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-06 00:55:35', '2025-06-06 00:55:37', 0, NULL, NULL),
(72, 'a58c2cb2-7036-48b3-9c34-88ef0ceae2c3', NULL, 2, 1, '456', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-06 00:55:41', '2025-06-06 00:55:42', 0, NULL, NULL),
(73, '5dd497ef-0b37-4dec-a3ef-3517819f3e3f', NULL, 2, 1, 'hihi', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-06 01:13:18', '2025-06-06 01:39:34', 0, NULL, NULL),
(74, '409345c3-1845-4cca-bac9-009e58e2f354', NULL, 2, 1, 'ok', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-06 01:15:22', '2025-06-06 01:15:24', 0, NULL, NULL),
(75, '6ad9c19c-dbd0-4fa5-bfbc-cb5fa7f36aeb', NULL, 2, 1, '123', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-06 01:16:19', '2025-06-06 01:16:21', 0, NULL, NULL),
(76, 'e8f32b70-ef38-49f0-92e6-5e6688aea639', NULL, 2, 1, '123', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-06 01:23:06', '2025-06-06 01:39:34', 0, NULL, NULL),
(77, 'ed00dff3-0369-4e6e-8918-55e7e63414a9', NULL, 2, 3, 'nói nhiều quá thế', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-06 01:39:42', '2025-06-06 02:10:16', 0, NULL, NULL),
(78, '47cdd431-9e2c-43dc-a105-19b34f5aa95c', NULL, 2, 3, 'éc', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-06 01:40:31', '2025-06-06 02:10:16', 0, NULL, NULL),
(79, 'e8b60418-3c79-4596-832c-e828c8e6024d', NULL, 2, 3, '123213', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-06 01:55:36', '2025-06-06 02:10:16', 0, NULL, NULL),
(80, '5c3cfddc-28e3-4317-a6f8-1cafbd9a668e', NULL, 2, 3, '123213', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-06 01:56:56', '2025-06-06 02:10:16', 0, NULL, NULL),
(81, 'e565353b-14b4-4214-9625-a8c81e7bee6e', NULL, 2, 1, '123', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-06 02:10:25', '2025-06-06 03:41:21', 0, NULL, NULL),
(82, '0e4f49be-43d6-4368-9568-ab7ef06c0ac4', NULL, 2, 1, '12345', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-06 02:13:37', '2025-06-06 03:41:32', 0, NULL, NULL),
(83, '2b4cf07c-c351-4117-989b-855691843d9b', NULL, 2, 1, '123', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-06 02:17:03', '2025-06-06 02:34:17', 0, NULL, NULL),
(84, '43f91d6b-5829-44a4-99cf-3ef2c03c71db', NULL, 2, 3, '123', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-06 02:32:55', '2025-06-06 03:40:38', 0, NULL, NULL),
(85, 'dafa8ba4-cd09-4e9e-b044-ffe4eac4a1b6', NULL, 2, 3, '123', NULL, 'CHAT', 'READ', NULL, 0, 0, NULL, NULL, NULL, '2025-06-06 02:33:03', '2025-06-06 02:33:05', 0, NULL, NULL);

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `message_deliveries`
--

CREATE TABLE `message_deliveries` (
  `id` bigint(20) NOT NULL,
  `message_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `status` enum('SENT','DELIVERED','READ') COLLATE utf8mb4_unicode_ci NOT NULL,
  `delivered_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `read_at` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Đang đổ dữ liệu cho bảng `message_deliveries`
--

INSERT INTO `message_deliveries` (`id`, `message_id`, `user_id`, `status`, `delivered_at`, `read_at`) VALUES
(1, 3, 2, 'READ', '2025-06-02 00:56:43', '2025-06-02 02:56:50'),
(2, 6, 2, 'READ', '2025-06-02 00:56:43', '2025-06-02 02:56:50'),
(3, 5, 1, 'READ', '2025-06-02 00:56:48', '2025-06-05 19:02:33'),
(4, 4, 1, 'READ', '2025-06-02 00:56:48', '2025-06-05 19:02:33'),
(5, 7, 1, 'READ', '2025-06-02 00:57:43', '2025-06-05 19:02:33'),
(7, 8, 1, 'READ', '2025-06-02 01:12:23', '2025-06-05 19:02:33'),
(8, 9, 1, 'READ', '2025-06-02 01:47:24', '2025-06-05 19:02:32'),
(9, 10, 3, 'READ', '2025-06-02 01:48:40', '2025-06-05 20:10:15'),
(10, 11, 3, 'READ', '2025-06-02 02:18:08', '2025-06-05 20:10:15'),
(11, 12, 2, 'READ', '2025-06-02 02:56:48', '2025-06-02 02:56:50'),
(12, 13, 4, 'READ', '2025-06-02 03:09:10', '2025-06-02 03:40:30'),
(13, 14, 1, 'READ', '2025-06-02 03:11:39', '2025-06-05 19:02:33'),
(14, 15, 1, 'READ', '2025-06-02 03:14:03', '2025-06-05 19:02:33'),
(15, 2, 1, 'READ', '2025-06-04 20:54:08', '2025-06-05 19:53:08'),
(16, 17, 3, 'READ', '2025-06-05 01:23:13', '2025-06-05 20:10:15'),
(17, 22, 3, 'READ', '2025-06-05 01:23:13', '2025-06-05 20:10:15'),
(18, 1, 3, 'READ', '2025-06-05 01:24:30', '2025-06-05 20:10:10'),
(19, 16, 3, 'READ', '2025-06-05 01:24:30', '2025-06-05 20:10:10'),
(20, 2, 3, 'READ', '2025-06-05 01:24:30', '2025-06-05 20:10:10'),
(21, 24, 1, 'READ', '2025-06-05 01:34:15', '2025-06-05 19:53:08'),
(22, 25, 3, 'READ', '2025-06-05 01:39:01', '2025-06-05 20:10:10'),
(23, 26, 1, 'READ', '2025-06-05 01:39:06', '2025-06-05 19:53:08'),
(24, 27, 1, 'READ', '2025-06-05 02:23:46', '2025-06-05 19:02:32'),
(25, 28, 3, 'READ', '2025-06-05 02:24:00', '2025-06-05 20:10:15'),
(26, 29, 3, 'READ', '2025-06-05 02:34:18', '2025-06-05 20:10:15'),
(27, 30, 1, 'READ', '2025-06-05 02:34:23', '2025-06-05 19:02:32'),
(28, 31, 3, 'READ', '2025-06-05 02:48:46', '2025-06-05 20:10:15'),
(29, 32, 3, 'READ', '2025-06-05 02:48:48', '2025-06-05 20:10:15'),
(30, 33, 1, 'READ', '2025-06-05 02:48:51', '2025-06-05 19:02:32'),
(31, 34, 1, 'READ', '2025-06-05 02:48:53', '2025-06-05 19:02:32'),
(32, 35, 1, 'READ', '2025-06-05 03:01:24', '2025-06-05 19:53:08'),
(33, 36, 1, 'READ', '2025-06-05 03:01:28', '2025-06-05 19:53:08'),
(34, 37, 3, 'READ', '2025-06-05 03:01:31', '2025-06-05 20:10:10'),
(35, 38, 1, 'READ', '2025-06-05 19:53:00', '2025-06-05 19:53:08');

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `message_reactions`
--

CREATE TABLE `message_reactions` (
  `id` bigint(20) NOT NULL,
  `message_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `type` enum('LIKE','LOVE','LAUGH','CRY','ANGRY','WOW') COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Đang đổ dữ liệu cho bảng `message_reactions`
--

INSERT INTO `message_reactions` (`id`, `message_id`, `user_id`, `type`, `created_at`) VALUES
(50, 15, 1, 'WOW', '2025-06-02 03:40:33'),
(51, 14, 1, 'LIKE', '2025-06-02 03:40:51'),
(52, 13, 4, 'LOVE', '2025-06-02 03:58:13'),
(53, 14, 4, 'LIKE', '2025-06-02 03:59:21'),
(54, 13, 1, 'LOVE', '2025-06-02 04:04:37'),
(55, 15, 4, 'LIKE', '2025-06-02 04:05:31'),
(56, 7, 1, 'LOVE', '2025-06-04 18:45:45'),
(57, 9, 3, 'ANGRY', '2025-06-04 18:45:53'),
(58, 10, 3, 'LIKE', '2025-06-04 18:46:11'),
(59, 8, 1, 'LIKE', '2025-06-04 18:46:23'),
(60, 9, 1, 'LIKE', '2025-06-04 18:46:38'),
(61, 10, 1, 'LIKE', '2025-06-04 18:46:42'),
(62, 11, 1, 'LIKE', '2025-06-04 18:52:30'),
(63, 11, 3, 'WOW', '2025-06-04 18:53:15'),
(64, 5, 1, 'LIKE', '2025-06-04 19:57:55'),
(65, 6, 1, 'LIKE', '2025-06-04 19:58:06'),
(66, 2, 1, 'LIKE', '2025-06-04 20:12:37'),
(67, 17, 1, 'LOVE', '2025-06-04 20:20:35'),
(68, 4, 1, 'LIKE', '2025-06-05 00:55:17'),
(69, 24, 1, 'LOVE', '2025-06-05 01:34:48'),
(70, 28, 1, 'LIKE', '2025-06-05 02:48:27'),
(71, 37, 1, 'LIKE', '2025-06-05 03:01:36'),
(72, 37, 3, 'ANGRY', '2025-06-05 03:01:40'),
(73, 26, 1, 'LIKE', '2025-06-05 03:03:37'),
(74, 34, 3, 'LIKE', '2025-06-05 03:19:45'),
(75, 16, 3, 'LIKE', '2025-06-05 19:54:35'),
(76, 23, 1, 'LOVE', '2025-06-06 00:01:34'),
(77, 84, 3, 'LIKE', '2025-06-06 03:38:39'),
(78, 83, 3, 'LIKE', '2025-06-06 03:40:58'),
(79, 82, 3, 'LIKE', '2025-06-06 03:41:05'),
(80, 81, 3, 'LIKE', '2025-06-06 03:41:08');

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `roles`
--

CREATE TABLE `roles` (
  `id` bigint(20) NOT NULL,
  `name` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Đang đổ dữ liệu cho bảng `roles`
--

INSERT INTO `roles` (`id`, `name`) VALUES
(2, 'ADMIN'),
(1, 'USER');

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `rooms`
--

CREATE TABLE `rooms` (
  `id` bigint(20) NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `avatar_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` enum('PRIVATE','GROUP') COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_encrypted` tinyint(1) DEFAULT '0',
  `is_archived` tinyint(1) DEFAULT '0',
  `is_pinned` tinyint(1) DEFAULT '0',
  `created_by` bigint(20) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `last_activity_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Đang đổ dữ liệu cho bảng `rooms`
--

INSERT INTO `rooms` (`id`, `name`, `description`, `avatar_url`, `type`, `is_encrypted`, `is_archived`, `is_pinned`, `created_by`, `created_at`, `updated_at`, `last_activity_at`) VALUES
(1, 'General Chat', 'Public chat room for everyone', NULL, 'GROUP', 0, 0, 0, 1, '2025-06-02 07:35:59', '2025-06-05 19:52:48', '2025-06-05 19:52:48'),
(2, 'Phòng 1', 'Phòng 1', NULL, 'GROUP', 0, 0, 0, 3, '2025-06-02 01:49:38', '2025-06-02 01:49:38', '2025-06-02 01:49:38'),
(3, 'Phòng 1', 'Phòng 1', NULL, 'PRIVATE', 0, 0, 0, 3, '2025-06-02 01:49:45', '2025-06-02 01:49:45', '2025-06-02 01:49:45'),
(4, '123123', '123123', NULL, 'GROUP', 0, 0, 0, 1, '2025-06-02 03:33:27', '2025-06-02 03:33:27', '2025-06-02 03:33:27'),
(5, 'Test tạo phòng', 'test tạo phòng', NULL, 'GROUP', 0, 0, 0, 1, '2025-06-04 20:46:58', '2025-06-04 20:46:58', '2025-06-04 20:46:58'),
(6, 'Test tạo phòng v2', 'hihi', NULL, 'GROUP', 0, 0, 0, 1, '2025-06-04 20:48:58', '2025-06-04 20:48:58', '2025-06-04 20:48:58'),
(7, 'Test tạo phòng v3', '123', NULL, 'GROUP', 0, 0, 0, 1, '2025-06-04 20:53:59', '2025-06-04 20:56:59', '2025-06-04 20:56:59'),
(8, 'Test thêm phòng private', 'Test thêm phòng private', NULL, 'PRIVATE', 0, 0, 0, 1, '2025-06-04 21:24:00', '2025-06-04 21:31:56', '2025-06-04 21:31:56'),
(9, 'Test cuộn 1', 'Test cuộn 1', NULL, 'GROUP', 0, 0, 0, 1, '2025-06-05 01:37:09', '2025-06-05 01:37:09', '2025-06-05 01:37:09'),
(10, 'Test cuộn 2', 'Test cuộn 2', NULL, 'GROUP', 0, 0, 0, 1, '2025-06-05 01:37:14', '2025-06-05 01:37:14', '2025-06-05 01:37:14');

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `room_members`
--

CREATE TABLE `room_members` (
  `id` bigint(20) NOT NULL,
  `room_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `role` enum('OWNER','ADMIN','MEMBER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_muted` tinyint(1) DEFAULT '0',
  `is_pinned` tinyint(1) DEFAULT '0',
  `joined_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_read_at` timestamp NULL DEFAULT NULL,
  `left_at` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Đang đổ dữ liệu cho bảng `room_members`
--

INSERT INTO `room_members` (`id`, `room_id`, `user_id`, `role`, `is_muted`, `is_pinned`, `joined_at`, `last_read_at`, `left_at`) VALUES
(1, 1, 1, 'OWNER', 0, 0, '2025-06-02 07:35:59', NULL, NULL),
(2, 1, 2, 'MEMBER', 0, 0, '2025-06-02 07:35:59', NULL, NULL),
(3, 1, 3, 'MEMBER', 0, 0, '2025-06-02 07:35:59', NULL, NULL),
(4, 2, 3, 'OWNER', 0, 0, '2025-06-02 01:49:38', NULL, NULL),
(5, 3, 3, 'OWNER', 0, 0, '2025-06-02 01:49:45', NULL, NULL),
(6, 4, 1, 'OWNER', 0, 0, '2025-06-02 03:33:27', NULL, NULL),
(7, 5, 1, 'OWNER', 0, 0, '2025-06-04 20:46:58', NULL, NULL),
(8, 6, 1, 'OWNER', 0, 0, '2025-06-04 20:48:58', NULL, NULL),
(9, 7, 1, 'OWNER', 0, 0, '2025-06-04 20:53:59', NULL, NULL),
(10, 8, 1, 'OWNER', 0, 0, '2025-06-04 21:24:00', NULL, NULL),
(11, 9, 1, 'OWNER', 0, 0, '2025-06-05 01:37:09', NULL, NULL),
(12, 10, 1, 'OWNER', 0, 0, '2025-06-05 01:37:14', NULL, NULL);

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `users`
--

CREATE TABLE `users` (
  `id` bigint(20) NOT NULL,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `full_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `avatar_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone_number` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bio` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enabled` tinyint(1) DEFAULT '1',
  `is_online` tinyint(1) DEFAULT '0',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_login` timestamp NULL DEFAULT NULL,
  `last_seen` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Đang đổ dữ liệu cho bảng `users`
--

INSERT INTO `users` (`id`, `username`, `email`, `password`, `full_name`, `avatar_url`, `phone_number`, `bio`, `enabled`, `is_online`, `created_at`, `last_login`, `last_seen`) VALUES
(1, 'admin', 'admin@example.com', '$2a$10$Ove0cvF/zaDAqAEksvlBKe//Vphd.cB88yEWk8y9DKjAXXdC68vh2', 'Administrator', NULL, NULL, NULL, 1, 1, '2025-06-02 07:35:59', '2025-06-06 03:37:43', '2025-06-05 01:22:55'),
(2, 'test', 'test@example.com', '$2a$10$Ove0cvF/zaDAqAEksvlBKe//Vphd.cB88yEWk8y9DKjAXXdC68vh2', 'Test User', NULL, NULL, NULL, 1, 0, '2025-06-02 07:35:59', '2025-06-02 02:56:34', '2025-06-02 02:56:56'),
(3, 'quandepzai', 'quan@example.com', '$2a$10$Ove0cvF/zaDAqAEksvlBKe//Vphd.cB88yEWk8y9DKjAXXdC68vh2', 'Vu Anh Quan', NULL, NULL, NULL, 1, 1, '2025-06-02 07:35:59', '2025-06-06 03:37:53', '2025-06-02 03:11:24'),
(4, 'quanxauzai', 'ntl.vaq@gmail.com', '$2a$10$Ove0cvF/zaDAqAEksvlBKe//Vphd.cB88yEWk8y9DKjAXXdC68vh2', 'Vu Anh Quan', NULL, '0865338476', NULL, 1, 1, '2025-06-02 00:37:20', '2025-06-02 03:11:30', '2025-06-02 03:10:54');

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `user_contacts`
--

CREATE TABLE `user_contacts` (
  `id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `contact_id` bigint(20) NOT NULL,
  `status` enum('PENDING','ACCEPTED','BLOCKED','DECLINED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `nickname` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_favorite` tinyint(1) DEFAULT '0',
  `is_blocked` tinyint(1) DEFAULT '0',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Đang đổ dữ liệu cho bảng `user_contacts`
--

INSERT INTO `user_contacts` (`id`, `user_id`, `contact_id`, `status`, `nickname`, `is_favorite`, `is_blocked`, `created_at`, `updated_at`) VALUES
(1, 1, 2, 'PENDING', NULL, 0, 0, '2025-06-02 00:42:43', '2025-06-02 00:42:43'),
(2, 2, 1, 'PENDING', NULL, 0, 0, '2025-06-02 00:55:52', '2025-06-02 00:55:52'),
(3, 4, 1, 'PENDING', NULL, 0, 0, '2025-06-02 04:19:51', '2025-06-02 04:19:51'),
(4, 1, 3, 'PENDING', NULL, 0, 0, '2025-06-04 20:20:52', '2025-06-04 20:20:52'),
(5, 1, 4, 'PENDING', NULL, 0, 0, '2025-06-05 03:05:44', '2025-06-05 03:05:44');

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `user_roles`
--

CREATE TABLE `user_roles` (
  `user_id` bigint(20) NOT NULL,
  `role_id` bigint(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Đang đổ dữ liệu cho bảng `user_roles`
--

INSERT INTO `user_roles` (`user_id`, `role_id`) VALUES
(1, 1),
(2, 1),
(3, 1),
(4, 1),
(1, 2);

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `user_sessions`
--

CREATE TABLE `user_sessions` (
  `id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `session_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `ip_address` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_agent` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `device_info` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_active` tinyint(1) DEFAULT '1',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_accessed_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `expired_at` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Chỉ mục cho các bảng đã đổ
--

--
-- Chỉ mục cho bảng `conversations`
--
ALTER TABLE `conversations`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_participant1` (`participant1_id`),
  ADD KEY `idx_participant2` (`participant2_id`),
  ADD KEY `idx_participants` (`participant1_id`,`participant2_id`),
  ADD KEY `idx_type` (`type`),
  ADD KEY `idx_last_message` (`last_message_at`);

--
-- Chỉ mục cho bảng `file_attachments`
--
ALTER TABLE `file_attachments`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_message` (`message_id`),
  ADD KEY `idx_file_name` (`file_name`),
  ADD KEY `idx_uploaded_by` (`uploaded_by`),
  ADD KEY `idx_file_type` (`file_type`);

--
-- Chỉ mục cho bảng `messages`
--
ALTER TABLE `messages`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `message_id` (`message_id`),
  ADD KEY `reply_to_id` (`reply_to_id`),
  ADD KEY `idx_message_id` (`message_id`),
  ADD KEY `idx_room_created` (`room_id`,`created_at`),
  ADD KEY `idx_conversation_created` (`conversation_id`,`created_at`),
  ADD KEY `idx_sender` (`sender_id`),
  ADD KEY `idx_type` (`type`),
  ADD KEY `idx_deleted` (`is_deleted`),
  ADD KEY `idx_created_at` (`created_at`),
  ADD KEY `fk_messages_pinned_by` (`pinned_by`),
  ADD KEY `idx_messages_pinned` (`is_pinned`,`created_at`),
  ADD KEY `idx_messages_room_pinned` (`room_id`,`is_pinned`,`pinned_at`),
  ADD KEY `idx_messages_conversation_pinned` (`conversation_id`,`is_pinned`,`pinned_at`),
  ADD KEY `idx_messages_room_created` (`room_id`,`created_at`,`is_deleted`),
  ADD KEY `idx_messages_conversation_created` (`conversation_id`,`created_at`,`is_deleted`);

--
-- Chỉ mục cho bảng `message_deliveries`
--
ALTER TABLE `message_deliveries`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `unique_message_user_delivery` (`message_id`,`user_id`),
  ADD UNIQUE KEY `UK4ks55leydeag6got35ag4y9i4` (`message_id`,`user_id`),
  ADD KEY `idx_user_status` (`user_id`,`status`),
  ADD KEY `idx_message_status` (`message_id`,`status`);

--
-- Chỉ mục cho bảng `message_reactions`
--
ALTER TABLE `message_reactions`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `unique_message_user_reaction` (`message_id`,`user_id`),
  ADD UNIQUE KEY `UKq21iskriotbxupl6p6wdhltf8` (`message_id`,`user_id`),
  ADD KEY `user_id` (`user_id`),
  ADD KEY `idx_message_type` (`message_id`,`type`);

--
-- Chỉ mục cho bảng `roles`
--
ALTER TABLE `roles`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `name` (`name`);

--
-- Chỉ mục cho bảng `rooms`
--
ALTER TABLE `rooms`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_type` (`type`),
  ADD KEY `idx_created_by` (`created_by`),
  ADD KEY `idx_last_activity` (`last_activity_at`),
  ADD KEY `idx_archived` (`is_archived`);

--
-- Chỉ mục cho bảng `room_members`
--
ALTER TABLE `room_members`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_room_user` (`room_id`,`user_id`),
  ADD KEY `idx_room_active` (`room_id`,`left_at`),
  ADD KEY `idx_user_rooms` (`user_id`,`left_at`);

--
-- Chỉ mục cho bảng `users`
--
ALTER TABLE `users`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `username` (`username`),
  ADD UNIQUE KEY `email` (`email`),
  ADD KEY `idx_username` (`username`),
  ADD KEY `idx_email` (`email`),
  ADD KEY `idx_enabled` (`enabled`),
  ADD KEY `idx_online` (`is_online`);

--
-- Chỉ mục cho bảng `user_contacts`
--
ALTER TABLE `user_contacts`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `unique_user_contact` (`user_id`,`contact_id`),
  ADD UNIQUE KEY `UK4juk2909tjnw4lx786wuqlere` (`user_id`,`contact_id`),
  ADD KEY `contact_id` (`contact_id`),
  ADD KEY `idx_user_status` (`user_id`,`status`),
  ADD KEY `idx_favorite` (`user_id`,`is_favorite`);

--
-- Chỉ mục cho bảng `user_roles`
--
ALTER TABLE `user_roles`
  ADD PRIMARY KEY (`user_id`,`role_id`),
  ADD KEY `role_id` (`role_id`);

--
-- Chỉ mục cho bảng `user_sessions`
--
ALTER TABLE `user_sessions`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `session_id` (`session_id`),
  ADD KEY `idx_session_id` (`session_id`),
  ADD KEY `idx_user_active` (`user_id`,`is_active`),
  ADD KEY `idx_expired` (`expired_at`);

--
-- AUTO_INCREMENT cho các bảng đã đổ
--

--
-- AUTO_INCREMENT cho bảng `conversations`
--
ALTER TABLE `conversations`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=4;
--
-- AUTO_INCREMENT cho bảng `file_attachments`
--
ALTER TABLE `file_attachments`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;
--
-- AUTO_INCREMENT cho bảng `messages`
--
ALTER TABLE `messages`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=86;
--
-- AUTO_INCREMENT cho bảng `message_deliveries`
--
ALTER TABLE `message_deliveries`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=36;
--
-- AUTO_INCREMENT cho bảng `message_reactions`
--
ALTER TABLE `message_reactions`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=81;
--
-- AUTO_INCREMENT cho bảng `roles`
--
ALTER TABLE `roles`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;
--
-- AUTO_INCREMENT cho bảng `rooms`
--
ALTER TABLE `rooms`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=11;
--
-- AUTO_INCREMENT cho bảng `room_members`
--
ALTER TABLE `room_members`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=13;
--
-- AUTO_INCREMENT cho bảng `users`
--
ALTER TABLE `users`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=5;
--
-- AUTO_INCREMENT cho bảng `user_contacts`
--
ALTER TABLE `user_contacts`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=6;
--
-- AUTO_INCREMENT cho bảng `user_sessions`
--
ALTER TABLE `user_sessions`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;
--
-- Các ràng buộc cho các bảng đã đổ
--

--
-- Các ràng buộc cho bảng `conversations`
--
ALTER TABLE `conversations`
  ADD CONSTRAINT `conversations_ibfk_1` FOREIGN KEY (`participant1_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `conversations_ibfk_2` FOREIGN KEY (`participant2_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Các ràng buộc cho bảng `file_attachments`
--
ALTER TABLE `file_attachments`
  ADD CONSTRAINT `file_attachments_ibfk_1` FOREIGN KEY (`message_id`) REFERENCES `messages` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `file_attachments_ibfk_2` FOREIGN KEY (`uploaded_by`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Các ràng buộc cho bảng `messages`
--
ALTER TABLE `messages`
  ADD CONSTRAINT `fk_messages_pinned_by` FOREIGN KEY (`pinned_by`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  ADD CONSTRAINT `messages_ibfk_1` FOREIGN KEY (`room_id`) REFERENCES `rooms` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `messages_ibfk_2` FOREIGN KEY (`conversation_id`) REFERENCES `conversations` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `messages_ibfk_3` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `messages_ibfk_4` FOREIGN KEY (`reply_to_id`) REFERENCES `messages` (`id`) ON DELETE SET NULL;

--
-- Các ràng buộc cho bảng `message_deliveries`
--
ALTER TABLE `message_deliveries`
  ADD CONSTRAINT `message_deliveries_ibfk_1` FOREIGN KEY (`message_id`) REFERENCES `messages` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `message_deliveries_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Các ràng buộc cho bảng `message_reactions`
--
ALTER TABLE `message_reactions`
  ADD CONSTRAINT `message_reactions_ibfk_1` FOREIGN KEY (`message_id`) REFERENCES `messages` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `message_reactions_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Các ràng buộc cho bảng `rooms`
--
ALTER TABLE `rooms`
  ADD CONSTRAINT `rooms_ibfk_1` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL;

--
-- Các ràng buộc cho bảng `room_members`
--
ALTER TABLE `room_members`
  ADD CONSTRAINT `room_members_ibfk_1` FOREIGN KEY (`room_id`) REFERENCES `rooms` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `room_members_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Các ràng buộc cho bảng `user_contacts`
--
ALTER TABLE `user_contacts`
  ADD CONSTRAINT `user_contacts_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `user_contacts_ibfk_2` FOREIGN KEY (`contact_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

--
-- Các ràng buộc cho bảng `user_roles`
--
ALTER TABLE `user_roles`
  ADD CONSTRAINT `user_roles_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `user_roles_ibfk_2` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE;

--
-- Các ràng buộc cho bảng `user_sessions`
--
ALTER TABLE `user_sessions`
  ADD CONSTRAINT `user_sessions_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
