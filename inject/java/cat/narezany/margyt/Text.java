package cat.narezany.margyt;

import java.util.Locale;

/**
 * The words the mod puts on screen, in the phone's language where it has them.
 *
 * Three languages and a handful of lines: enough that the mod does not look
 * bolted onto a Russian phone in English, and small enough to keep in one file
 * rather than in string resources -- which this build cannot add.
 */
final class Text {

    private Text() {}

    private static final boolean RU = "ru".equals(language()) || "be".equals(language());
    private static final boolean UK = "uk".equals(language());
    static final String PINNED_FRIENDS=pick("Закреплённые друзья","Закріплені друзі","Pinned friends");
    static final String FRIEND_CANDIDATES=pick("Друзья","Друзі","Friends");
    static final String MOVE_UP=pick("Выше","Вище","Move up");
    static final String MOVE_DOWN=pick("Ниже","Нижче","Move down");
    static final String UNPIN=pick("Открепить","Відкріпити","Unpin");
    static final String PIN_LIMIT=pick("Можно закрепить до 20 друзей","Можна закріпити до 20 друзів","Pin up to 20 friends");
    static final String FRIENDS_LOAD_HINT=pick("Сначала откройте список друзей TikTok. Из него появятся доступные профили.","Спочатку відкрийте список друзів TikTok.","Open the TikTok friends list first to load available profiles.");
    static final String NOTHING_FOUND=pick("Ничего не найдено","Нічого не знайдено","No matches");
    static final String PROFILE_LAYOUT=pick("Вид профиля","Вигляд профілю","Profile layout");
    static final String LAYOUT_NATIVE=pick("Обычный","Звичайний","Original");
    static final String LAYOUT_COMPACT=pick("Компактный","Компактний","Compact");
    static final String LAYOUT_EXPANDED=pick("Расширенный","Розширений","Expanded");
    static final String MESSAGE_TIMES=pick("Время сообщений","Час повідомлень","Message timestamps");
    static final String NOTIFICATIONS=pick("Уведомления","Сповіщення","Notifications");
    static final String NOTIFY_MESSAGES=pick("Сообщения","Повідомлення","Messages");
    static final String NOTIFY_PROMOS=pick("Промоуведомления","Промосповіщення","Promotional notifications");
    static final String NOTIFY_OTHER=pick("Другие уведомления","Інші сповіщення","Other notifications");
    static final String NOTIFY_NO_PROMPTS=pick("Без напоминаний о разрешении","Без нагадувань про дозвіл","No permission reminders");
    static final String NOTIFY_SETTINGS=pick("Разрешение Android","Дозвіл Android","Android permission");
    static final String REPOST=pick("Репост","Репост","Repost");
    static final String REPOST_POSITION=pick("Расположение репоста","Розташування репосту","Repost placement");
    static final String REPOST_FAVORITE=pick("Вместо избранного","Замість обраного","Replace favorite");
    static final String REPOST_AVATAR=pick("Над аватаром","Над аватаром","Above avatar");
    static final String REPOST_FREE=pick("Рядом с избранным","Поруч з обраним","Beside favorite");
    static final String REPOST_NOT_READY=pick("Штатный репост недоступен","Штатний репост недоступний","Native repost is unavailable");
    static final String BLOCK_SUGGESTION=pick("Скрыть такие предложения","Приховати такі пропозиції","Hide this suggestion type");
    static final String VIDEO_ACTIONS=pick("Действия видео","Дії відео","Video actions");
    static final String ZEN_MODE=pick("Режим без интерфейса","Режим без інтерфейсу","Zen Mode");
    static final String ZEN_MODE_ON=pick("Скрыть элементы поверх видео","Приховати елементи поверх відео","Hide video overlays");
    static final String ZEN_MODE_OFF=pick("Показать элементы поверх видео","Показати елементи поверх відео","Restore video overlays");
    static final String COPY_VIDEO_LINK=pick("Копировать ссылку на видео","Копіювати посилання на відео","Copy video link");
    static final String CLEAN_LINKS=pick("Удалять параметры отслеживания из ссылок","Видаляти параметри відстеження з посилань","Remove tracking parameters from links");
    static final String COPY_METADATA=pick("Копировать данные видео","Копіювати дані відео","Copy video details");
    static final String VIDEO_ID=pick("ID видео","ID відео","Video ID");
    static final String VIDEO_AUTHOR=pick("Автор","Автор","Author");
    static final String VIDEO_SOUND=pick("Звук","Звук","Sound");
    static final String VIDEO_PUBLISHED=pick("Время публикации","Час публікації","Published");
    static final String VIDEO_NOT_READY=pick("Откройте видео в ленте","Відкрийте відео у стрічці","Open a feed video first");
    static final String SAVE_VIDEO=pick("Скачать видео","Завантажити відео","Download video");
    static final String SAVE_AUDIO=pick("Скачать аудио","Завантажити аудіо","Download audio");
    static final String SAVE_IMAGE=pick("Скачать фото","Завантажити фото","Download photo");
    static final String SAVE_FRAME=pick("Сохранить кадр","Зберегти кадр","Save current frame");
    static final String FRAME_NOT_AVAILABLE=pick("Не удалось получить кадр: поверхность недоступна","Не вдалося отримати кадр: поверхня недоступна","Cannot capture this player surface");
    static final String MEDIA_NOT_ALLOWED=pick("Автор ограничил скачивание","Автор обмежив завантаження","The author restricted downloads");
    static final String AUDIO_NOT_AVAILABLE=pick("В этом видео нет отдельной аудиодорожки","У цьому відео немає окремої аудіодоріжки","No separate audio track is available");
    static final String PHOTO_NOT_AVAILABLE=pick("Фото недоступно для скачивания","Фото недоступне для завантаження","Photo is unavailable");
    static final String MEDIA_FAILED=pick("Не удалось сохранить файл. Подробности в диагностике","Не вдалося зберегти файл. Подробиці в діагностиці","Could not save the file. See diagnostics");
    static final String DOWNLOADING=pick("Загрузка","Завантаження","Downloading");
    static final String PLAYER_SPEED=pick("Скорость удержания","Швидкість утримання","Hold speed");
    static final String CUSTOM_SPEED=pick("Своё значение","Власне значення","Custom value");
    static final String SPEED_RANGE=pick("Допустимая скорость: 0.5–3×","Допустима швидкість: 0.5–3×","Speed must be between 0.5 and 3×");
    static final String PLAYER_NOT_READY=pick("Плеер ещё не готов","Плеєр ще не готовий","The player is not ready yet");
    static final String SEEK_BACK=pick("Назад на выбранный шаг","Назад на вибраний крок","Seek back one step");
    static final String SEEK_FORWARD=pick("Вперёд на выбранный шаг","Уперед на вибраний крок","Seek forward one step");
    static final String SEEK_SMOOTH=pick("Плавно назад","Плавно назад","Smooth rewind");
    static final String SEEK_STEP=pick("Шаг перемотки, секунды","Крок перемотування, секунди","Seek step, seconds");
    static final String REVERSE_SPEED=pick("Скорость перемотки назад","Швидкість перемотування назад","Rewind rate");
    static final String BLOCK_AUTHOR=pick("Не предлагать этого автора","Не пропонувати цього автора","Hide this author");
    static final String BLOCK_LIMIT=pick("В списке уже 50 авторов","У списку вже 50 авторів","The list already contains 50 authors");
    static final String HIDE_STORIES=pick("Скрывать истории","Приховувати історії","Hide stories");
    static final String HIDE_SUGGESTIONS=pick("Скрывать карточки рекомендаций","Приховувати картки рекомендацій","Hide recommendation cards");
    static final String DL_QUALITY=pick("Качество загрузки","Якість завантаження","Download quality");
    static final String DL_NATIVE=pick("Штатное","Стандартна","Original");
    static final String DL_MAX=pick("Максимальное доступное","Максимальна доступна","Highest available");
    static final String DL_LOW=pick("Минимальное доступное","Мінімальна доступна","Lowest available");
    static final String DL_AUDIO=pick("Только звук","Лише звук","Audio only");
    static final String FRAME_QUALITY=pick("Размер кадра","Розмір кадру","Frame size");
    static final String VIDEO_ACTIONS_HINT=pick("Удерживайте «Отправить» на видео, чтобы открыть действия TokkiYou.","Утримуйте «Надіслати» на відео, щоб відкрити дії TokkiYou.","Hold Share on a video to open TokkiYou actions.");
    static final String APPLY=pick("Применить","Застосувати","Apply");
    static final String RAIL=pick("Кнопки видео","Кнопки відео","Video buttons");
    static final String RAIL_LIKE=pick("Лайк","Вподобайка","Like");
    static final String RAIL_COMMENT=pick("Комментарии","Коментарі","Comments");
    static final String RAIL_FAV=pick("Избранное","Обране","Favourites");
    static final String RAIL_SHARE=pick("Отправить","Надіслати","Share");
    static final String COMMENT_COPY_SELECTED=pick("Копировать выделенное","Копіювати виділене","Copy selection");
    static final String COMMENT_SELECT_HINT=pick("Выделите нужный фрагмент текста","Виділіть потрібний фрагмент тексту","Select a text fragment first");
    static final String HIDDEN_ITEMS=pick("Что скрыто","Що приховано","Hidden items");
    static final String HIDDEN_ITEMS_NOTE=pick("Вернуть элементы в ленту","Повернути елементи до стрічки","Restore feed items");
    static final String HIDDEN_EMPTY=pick("Ничего не скрыто","Нічого не приховано","Nothing is hidden");
    static final String HIDDEN_RESTORE=pick("Вернуть","Повернути","Restore");
    static final String HIDDEN_RESTORE_ALL=pick("Вернуть всё","Повернути все","Restore all");
    static final String HIDDEN_NEXT_PAGE=pick("Изменения применятся при следующей загрузке ленты.","Зміни застосуються під час наступного завантаження стрічки.","Changes apply when the feed next loads.");

    static final String ROW = pick("Настройки TokkiYou", "Налаштування TokkiYou", "TokkiYou settings");
    static final String UPDATE_UNAVAILABLE = pick(
            "Источник обновлений TokkiYou ещё не настроен",
            "Джерело оновлень TokkiYou ще не налаштовано",
            "The TokkiYou update source is not configured yet");
    static final String DIAGNOSTICS = pick("Диагностика", "Діагностика", "Diagnostics");
    static final String COPY_DIAGNOSTICS = pick("Копировать диагностику",
            "Копіювати діагностику", "Copy diagnostics");
    static final String PROFILE_STYLE = pick("Оформление профиля",
            "Оформлення профілю", "Profile style");
    static final String LITE_GOOGLE = pick(
            "Вход через Google в TokkiYou Lite не работает. Войди по почте или номеру телефона.",
            "Вхід через Google у TokkiYou Lite не працює. Увійди за поштою або номером телефону.",
            "Signing in with Google does not work in TokkiYou Lite. Use your e-mail or phone number.");
    static final String APPEARANCE = pick("Внешний вид", "Зовнішній вигляд", "Appearance");
    static final String PROFILE_PREVIEW = pick("Так значок будет выглядеть у ника",
            "Так значок виглядатиме біля імені", "Badge preview beside nickname");
    static final String PROFILE_PREVIEW_NICK = pick("@ваш_ник", "@ваше_ім’я", "@your_name");
    static final String PROFILE_PREVIEW_EMPTY = pick("Реальный профиль TikTok",
            "Справжній профіль TikTok", "Your real TikTok profile");
    static final String PROFILE_PREVIEW_LOADING = pick(
            "Откройте свой профиль TikTok — имя и фото появятся здесь",
            "Відкрийте свій профіль TikTok — ім’я та фото з’являться тут",
            "Open your TikTok profile to load its real name and photo");
    static final String PROFILE_PREVIEW_REAL = pick("Предпросмотр вашего профиля TikTok",
            "Попередній перегляд вашого профілю TikTok",
            "Preview of your TikTok profile");
    static final String PROFILE_EYEDROPPER = pick(
            "Пипетка: коснитесь изображения значка, чтобы взять цвет",
            "Піпетка: торкніться зображення значка, щоб взяти колір",
            "Eyedropper: tap the badge image to sample a colour");
    static final String PROFILE_PICK_COLOUR = pick("Выберите цвет", "Виберіть колір", "Choose colour");
    static final String PROFILE_BADGE = pick("Значок у ника", "Значок біля імені",
            "Badge beside nickname");
    static final String PROFILE_BADGE_REMOVE = pick("Удалить значок", "Видалити значок",
            "Remove badge");
    static final String PROFILE_CROP_X = pick("Положение по горизонтали",
            "Положення по горизонталі", "Horizontal position");
    static final String PROFILE_CROP_Y = pick("Положение по вертикали",
            "Положення по вертикалі", "Vertical position");
    static final String PROFILE_CROP_ZOOM = pick("Масштаб значка",
            "Масштаб значка", "Badge scale");
    static final String PROFILE_GRADIENT = pick("Градиент ника", "Градієнт імені",
            "Nickname gradient");
    static final String PROFILE_START = pick("Первый цвет", "Перший колір", "First colour");
    static final String PROFILE_END = pick("Второй цвет", "Другий колір", "Second colour");
    static final String PROFILE_MIDDLE = pick("Центральный цвет", "Центральний колір", "Middle colour");
    static final String PROFILE_THREE = pick("Градиент из трёх цветов", "Градієнт із трьох кольорів", "Three-colour gradient");
    static final String PROFILE_DIRECTION = pick("Направление градиента", "Напрямок градієнта", "Gradient direction");
    static final String PROFILE_DIRECTION_RIGHT = pick("Слева направо", "Зліва направо", "Left to right");
    static final String PROFILE_DIRECTION_DOWN = pick("Сверху вниз", "Згори вниз", "Top to bottom");
    static final String PROFILE_DIRECTION_DIAGONAL = pick("По диагонали", "По діагоналі", "Diagonal");
    static final String PROFILE_DIRECTION_REVERSE = pick("Диагональ в обратную сторону", "Діагональ у зворотний бік", "Reverse diagonal");
    static final String PROFILE_BOLD = pick("Полужирный ник", "Напівжирне ім’я", "Bold nickname");
    static final String PROFILE_GLOW = pick("Мягкое свечение", "М’яке сяйво", "Subtle glow");
    static final String PROFILE_ANIMATED = pick("Анимировать градиент", "Анімувати градієнт", "Animate gradient");
    static final String PROFILE_SPEED = pick("Скорость градиента", "Швидкість градієнта", "Gradient speed");
    static final String PROFILE_SHARE = pick("Показывать стиль другим TokkiYou",
            "Показувати стиль іншим TokkiYou", "Share style with other TokkiYou users");
    static final String PROFILE_SHARE_WAIT = pick(
            "Общий стиль станет доступен после подключения HTTPS-сервера TokkiYou",
            "Спільний стиль стане доступним після підключення HTTPS-сервера TokkiYou",
            "Shared style needs a TokkiYou HTTPS service");
    static final String STORE_OFFLINE = pick("Магазин плагинов недоступен без HTTPS-сервера",
            "Магазин плагінів недоступний без HTTPS-сервера",
            "Plugin store needs an HTTPS service");
    static final String PROFILE_RESET = pick("Сбросить оформление", "Скинути оформлення",
            "Reset profile style");
    static final String PROFILE_BADGE_FAILED = pick("Не удалось прочитать изображение",
            "Не вдалося прочитати зображення", "Could not read the image");
    static final String THEME_PRESET_DARK = pick("Классический пресет",
            "Класичний пресет", "Classic preset");
    static final String THEME_PRESET_BLUE = pick("Синий пресет",
            "Синій пресет", "Blue preset");
    static final String THEME_RESET = pick("Сбросить к теме TikTok",
            "Скинути до теми TikTok", "Reset to TikTok theme");
    static final String THEME_PRESETS = pick("Готовые темы", "Готові теми", "Theme presets");
    static final String THEME_PRESETS_NOTE = pick("Классическая, синяя или исходная тема TikTok",
            "Класична, синя або початкова тема TikTok",
            "Classic, blue or original TikTok theme");
    static final String OPEN_TTCUZ_SITE = pick("Инструкции на сайте TokkiYou",
            "Інструкції на сайті TokkiYou", "Instructions on the TokkiYou site");
    static final String THEME_COLOURS = pick("Цвета интерфейса",
            "Кольори інтерфейсу", "Interface colours");
    static final String THEME_COLOURS_NOTE = pick(
            "Цвета сохраняются отдельно для светлой и тёмной темы. Применение зависит от имён элементов в этой версии TikTok.",
            "Кольори зберігаються окремо для світлої й темної теми. Застосування залежить від назв елементів у цій версії TikTok.",
            "Colours are saved separately for light and dark mode. Applying them depends on view names in this TikTok version.");

    static final String REGION = pick("Регион", "Регіон", "Region");

    static final String CHANGE_REGION = pick(
            "Менять регион", "Змінювати регіон", "Change the region");

    static final String COUNTRY = pick("Страна", "Країна", "Country");

    static final String ACCENT = pick("Акцент", "Акцент", "Accent");

    static final String ACCENT_COLOUR = pick("Акцент", "Акцент", "Accent");
    static final String LIKE_COLOUR = pick("Цвет лайков", "Колір вподобань", "Like colour");
    static final String LIKE_RESET = pick("Цвет TikTok по умолчанию", "Типовий колір TikTok",
            "TikTok default colour");
    static final String CUSTOM_COLOUR = pick("Свой цвет #RRGGBB",
            "Свій колір #RRGGBB", "Custom colour #RRGGBB");
    static final String ACCENT_PICKER = pick("Выберите цвет акцента",
            "Виберіть колір акценту", "Choose accent colour");

    static final String ACCENT_NOTE = pick(
            "Меняет розовый, которым TikTok рисует лайки, кнопки и вкладки. "
                    + "Часть значков нарисована картинками — их цвет задаётся при сборке "
                    + "и здесь не меняется.",
            "Змінює рожевий, яким TikTok малює лайки, кнопки та вкладки. "
                    + "Частина значків намальована картинками — їхній колір задається "
                    + "під час збірки і тут не змінюється.",
            "Changes the pink TikTok draws likes, buttons and tabs with. Some icons "
                    + "are pictures rather than code; their colour is settled at build "
                    + "time and does not follow.");

    /** The bar at the foot of the screen: one line, not a paragraph. */
    static final String RESTART_PENDING = pick(
            "Нужен перезапуск",
            "Потрібен перезапуск",
            "Restart needed");

    static final String RESTART = pick("Перезапустить", "Перезапустити", "Restart");

    // ------------------------------------------------ what TikTok ships off

    static final String HIDDEN = pick("Анти A/B", "Анти A/B", "Anti A/B");

    static final String HIDDEN_NOTE = pick(
            "Функции у TikTok уже написаны, но выдаются случайной части людей. "
                    + "Здесь они включаются всем.",
            "Функції в TikTok уже написані, але видаються випадковій частині людей. "
                    + "Тут вони вмикаються всім.",
            "TikTok has written these already and hands them to a random share of "
                    + "people. Here they are switched on for everyone.");

    static final String CLOSE = pick("Понятно", "Зрозуміло", "Got it");

    static final String SAVE_STICKER = pick(
            "Скачать стикер", "Завантажити стікер", "Download the sticker");

    static final String STICKER_FAILED = pick(
            "Не получилось сохранить стикер", "Не вдалося зберегти стікер",
            "Could not save the sticker");

    static final String SAVE_AVATARS_ON = pick(
            "Кнопка на аватарках", "Кнопка на аватарках", "The button on avatars");

    static final String SAVE_STICKERS_ON = pick(
            "Кнопка на стикерах", "Кнопка на стікерах", "The button on stickers");

    static final String COMMENTS = pick("Комментарии", "Коментарі", "Comments");
    static final String COMMENT_COPY_ON = pick("Копирование комментариев", "Копіювання коментарів", "Copy comments");
    static final String COMMENT_COPY = pick("Скопировать комментарий", "Скопіювати коментар", "Copy comment");
    static final String COMMENT_SELECT = pick("Выбрать фрагмент", "Вибрати фрагмент", "Select part");
    static final String COMMENT_SELECT_COPY = pick("Выделить и копировать", "Виділити й копіювати", "Select and copy");
    static final String COMMENT_TIKTOK = pick("Действия TikTok", "Дії TikTok", "TikTok actions");
    static final String COMMENT_COPIED = pick("Комментарий скопирован", "Коментар скопійовано", "Comment copied");

    static final String BADGES_OF_ACCOUNT = pick("Все значки аккаунта", "Усі значки акаунта",
            "All badges of this account");
    static final String BADGES_ON = pick("Значки", "Значки", "Badges");

    static final String SAVE_AVATAR = pick(
            "Сохранить аватарку", "Зберегти аватарку", "Save the avatar");

    static final String SAVED = pick("Сохранено", "Збережено", "Saved");

    static final String AVATAR_NOTHING = pick(
            "Нечего сохранять — откройте аватарку сначала",
            "Нема чого зберігати — відкрийте аватарку спершу",
            "Nothing to save yet -- open an avatar first");

    static final String AVATAR_FAILED = pick(
            "Не получилось сохранить", "Не вдалося зберегти", "Could not save it");

    static final String BADGE_OWNER = pick(
            "Автор исходного проекта", "Автор вихідного проєкту",
            "Author of the original project");
    static final String MY_BADGE_TITLE = "TokkiYou";
    static final String MY_BADGE_TEXT = pick(
            "Участник TokkiYou", "Учасник TokkiYou", "TokkiYou member");

    static final String VIDEO = pick("Видео", "Відео", "Video");

    static final String THEME = pick("Тема", "Тема", "Theme");

    static final String LOADING_SHAPES = pick(
            "Загрузка фигурами", "Завантаження фігурами", "Shapes while loading");

    static final String LOADING_NOTE = pick(
            "Вместо двух кружков TikTok крутятся фигуры Material 3 в цвете акцента.",
            "Замість двох кружечків TikTok крутяться фігури Material 3 у кольорі акценту.",
            "Material 3 shapes in the accent colour, in place of TikTok's two circles.");

    static final String ROUND_CARDS = pick(
            "Скруглённые карточки видео", "Заокруглені картки відео", "Rounded video cards");

    static final String THEME_MATERIAL = pick(
            "Цвета своего телефона", "Кольори свого телефона", "Your phone's own colours");

    static final String THEME_OLD = pick(
            "Нужен Android 12 или новее: раньше у телефона нет своей палитры.",
            "Потрібен Android 12 або новіший: раніше телефон не має своєї палітри.",
            "Needs Android 12 or newer: before that a phone has no palette of its own.");

    static final String THEME_NOTE = pick(
            "Перекрашивается только то, что TikTok и сам перекрашивает при "
                    + "смене светлой темы на тёмную. Акцент живёт отдельно.",
            "Перефарбовується лише те, що TikTok і сам перефарбовує при зміні "
                    + "світлої теми на темну. Акцент живе окремо.",
            "Only what TikTok itself repaints when you switch between light and "
                    + "dark. The accent is its own thing.");

    static final String BACKGROUND = pick(
            "Играть в фоне", "Грати у фоні", "Play in the background");

    static final String AUTOSCROLL = pick(
            "Автопрокрутка ленты", "Автопрокрутка стрічки", "Scroll the feed by itself");

    static final String SOUND = pick(
            "Звук, снятый по копирайту", "Звук, знятий за копірайтом",
            "Sound pulled for copyright");

    static final String SEEKBAR = pick(
            "Перемотка на всех видео", "Перемотка на всіх відео",
            "The scrubbing bar everywhere");

    static final String VOICE = pick(
            "Голосовые комментарии", "Голосові коментарі", "Voice comments");

    static final String ACCENT_WALLPAPER = pick(
            "Цвет своего телефона", "Колір свого телефона", "Your phone's own colour");

    // --------------------------------------------------------------- fonts

    static final String FONT = pick("Шрифт", "Шрифт", "Typeface");

    // ----------------------------------------------------- the test builds

    static final String TEST_NOBODY = pick("не вошёл", "не увійшов", "not signed in");

    static final String TEST_TITLE = pick(
            "Тестовая сборка", "Тестова збірка", "A test build");

    static final String TEST_TEXT = pick(
            "Это тестовая сборка TokkiYou — она только для тех, кто поддержал "
                    + "разработку. Настройки мода откроются на аккаунте со значком "
                    + "поддержавшего; TikTok работает как обычно. Если значка нет, "
                    + "поставьте обычную версию с сайта.",
            "Це тестова збірка TokkiYou — вона лише для тих, хто підтримав "
                    + "розробку. Налаштування мода відкриються на акаунті зі значком "
                    + "того, хто підтримав; TikTok працює як завжди. Якщо значка "
                    + "немає, встановіть звичайну версію з сайту.",
            "This is a test build of TokkiYou, and it is for the people who paid "
                    + "for the work. The mod's settings open on an account with the "
                    + "supporter badge; TikTok itself works as usual. Without the "
                    + "badge, install the ordinary version from the site.");

    static final String TEST_CLOSE = pick("Понятно", "Зрозуміло", "I see");

    static final String OWN_BRIGHTNESS = pick(
            "TikTok не меняет яркость", "TikTok не змінює яскравість", "TikTok leaves the brightness alone");

    static final String OWN_BRIGHTNESS_NOTE = pick(
            "TikTok сам выкручивает яркость на максимум в камере и на HDR-видео. "
                    + "С этой настройкой она остаётся такой, какую выставил ты. "
                    + "HDR-видео играют как обычные.",
            "TikTok сам викручує яскравість на максимум у камері й на HDR-відео. "
                    + "Із цим налаштуванням вона лишається такою, яку виставив ти. "
                    + "HDR-відео грають як звичайні.",
            "TikTok turns the brightness all the way up in the camera and on HDR videos. "
                    + "With this on it stays where you set it. HDR videos play as ordinary ones.");

    // ------------------------------------------------------------ the gradient

    static final String GRADIENT = pick("Градиент на ник", "Градієнт на нік", "Name gradient");

    static final String GRADIENT_NOTE = pick(
            "Ник в градиенте видят все, у кого стоит TokkiYou. Менять можно раз в минуту.",
            "Нік у градієнті бачать усі, у кого стоїть TokkiYou. Міняти можна раз на хвилину.",
            "Everyone running TokkiYou sees it. Changing is allowed once a minute.");

    static final String GRADIENT_ONLY = pick(
            "Нужен подтверждённый аккаунт",
            "Потрібен підтверджений акаунт",
            "A verified account is required");

    static final String GRADIENT_HUE = pick("Цвет", "Колір", "Hue");
    static final String GRADIENT_SAT = pick("Насыщенность", "Насиченість", "Saturation");
    static final String GRADIENT_VALUE = pick("Яркость", "Яскравість", "Brightness");

    static final String GRADIENT_ADD = pick("Добавить цвет", "Додати колір", "Add a colour");
    static final String GRADIENT_DROP_ONE = pick("Убрать цвет", "Прибрати колір", "Remove a colour");
    static final String GRADIENT_SAVE = pick("Сохранить градиент", "Зберегти градієнт", "Save it");
    static final String GRADIENT_OFF = pick("Убрать градиент", "Прибрати градієнт", "Take it off");

    static final String GRADIENT_SAVED = pick("Готово", "Готово", "Done");
    static final String GRADIENT_REFUSED = pick(
            "Не удалось сохранить градиент. Проверь интернет и попробуй ещё раз.",
            "Не вдалося зберегти градієнт. Перевір інтернет і спробуй ще раз.",
            "Could not save the gradient. Check your connection and try again.");
    static String gradientWait(int seconds) {
        return pick("Попробуй снова через " + seconds + " с",
                "Спробуй знову за " + seconds + " с",
                "Try again in " + seconds + " s");
    }
    static final String GRADIENT_NO_ACCOUNT = pick(
            "Сначала войди в аккаунт", "Спочатку увійди в акаунт", "Sign in first");

    // ------------------------------------------------ proving an account is yours

    static final String PROVE = pick(
            "Подтвердить аккаунт", "Підтвердити акаунт", "Prove the account is yours");

    static final String PROVE_NOTE = pick(
            "Без этого сервер не даст менять значки и градиент.",
            "Без цього сервер не дасть змінювати значки й градієнт.",
            "Without it the server will not let anything be changed.");

    static final String PROVE_DONE = pick(
            "Аккаунт подтверждён", "Акаунт підтверджено", "Account proved");

    static final String PROVE_DONE_NOTE = pick(
            "Код можно убрать из описания профиля.",
            "Код можна прибрати з опису профілю.",
            "The code can come out of the bio now.");

    static final String PROVE_HOW = pick(
            "1. Скопируй код.\n"
                    + "2. Вставь его в описание профиля в TikTok и сохрани.\n"
                    + "3. Вернись сюда и нажми «Проверить».\n"
                    + "Потом код можно убрать из описания.",
            "1. Скопіюй код.\n"
                    + "2. Встав його в опис профілю в TikTok і збережи.\n"
                    + "3. Повернись сюди і натисни «Перевірити».\n"
                    + "Потім код можна прибрати з опису.",
            "1. Copy the code.\n"
                    + "2. Put it in your TikTok bio and save.\n"
                    + "3. Come back here and press Check.\n"
                    + "The code can come out afterwards.");

    static final String PROVE_WHY = pick(
            "Так сервер убеждается, что аккаунт твой: в чужое описание никто "
                    + "написать не может.",
            "Так сервер переконується, що акаунт твій: у чужий опис ніхто "
                    + "написати не може.",
            "It is the one thing only the owner of an account can do.");

    static final String PROVE_COPY = pick("Код скопирован", "Код скопійовано",
            "Code copied");

    static final String PROVE_TAP = pick(
            "Нажми на код, чтобы скопировать",
            "Натисни на код, щоб скопіювати",
            "Tap the code to copy it");

    static final String PROVE_FRESH = pick(
            "Другой код", "Інший код", "A different code");

    static final String PROVE_FRESH_DONE = pick(
            "Код заменён, вставь новый в описание",
            "Код замінено, встав новий в опис",
            "Here is another one, put it in the bio");

    static final String PROVE_CHECKING = pick(
            "Смотрю твою страницу…", "Дивлюсь твою сторінку…", "Reading your page...");

    static final String PROVE_NEED_NAME = pick(
            "Не могу найти твою страницу по айди. Впиши свой ник и жми ещё раз.",
            "Не можу знайти твою сторінку за айді. Впиши свій нік і тисни ще раз.",
            "Your page cannot be found from the id. Write your name and press again.");

    static final String PROVE_NAME = pick("твой ник без @", "твій нік без @",
            "your name without the @");

    static final String PROVE_NOT_YOURS = pick(
            "Этот код просили не с этого телефона. Открой карточку заново.",
            "Цей код просили не з цього телефона. Відкрий картку заново.",
            "This code was asked for by another phone. Open the card again.");

    static final String PROVE_HIDDEN = pick(
            "Если TikTok прячет описание от других, впиши код в имя профиля: "
                    + "оно обновляется сразу.",
            "Якщо TikTok ховає опис від інших, впиши код в ім'я профілю: "
                    + "воно оновлюється одразу.",
            "If TikTok is holding your bio back, put the code in your profile "
                    + "name instead: that shows up at once.");

    static final String PROVE_CHECK = pick("Проверить", "Перевірити", "Check");

    static final String PROVE_OK = pick(
            "Готово, аккаунт твой", "Готово, акаунт твій", "Done, it is yours");

    static final String PROVE_NOT_THERE = pick(
            "Кода в описании не видно. Сохрани описание и жми ещё раз, "
                    + "TikTok обновляет страницу не сразу.",
            "Коду в описі не видно. Збережи опис і тисни ще раз, "
                    + "TikTok оновлює сторінку не одразу.",
            "The code is not on the page yet. Save the bio and press again, "
                    + "TikTok takes a moment to show it.");

    static final String PROVE_OTHER_NAME = pick(
            "Этот ник принадлежит другому аккаунту",
            "Цей нік належить іншому акаунту",
            "That name belongs to another account");

    static final String PROVE_NO_TIKTOK = pick(
            "TikTok не ответил, попробуй ещё раз",
            "TikTok не відповів, спробуй ще раз",
            "TikTok did not answer, try again");

    static final String PROVE_NO_SERVER = pick(
            "Сервер не отвечает", "Сервер не відповідає", "The server is not answering");

    static final String PROVE_STALE = pick(
            "Код устарел. Вот новый, вставь его.",
            "Код застарів. Ось новий, встав його.",
            "That code ran out. Here is a new one.");

    static final String PROVE_TOO_OFTEN = pick(
            "Слишком часто, подожди немного", "Занадто часто, зачекай трохи",
            "Too often, wait a moment");

    static final String PROVE_NO_ACCOUNT = pick(
            "Сначала войди в аккаунт", "Спочатку увійди в акаунт", "Sign in first");

    static final String PROVE_FAILED = pick(
            "Не вышло", "Не вийшло", "That did not work");

    // ----------------------------------------------- fixes without an apk

    static final String MADE_BY = pick(
            "Сделал @mrretizixd · GPL-3.0 с доп. условиями",
            "Зробив @mrretizixd · GPL-3.0 з дод. умовами",
            "Made by @mrretizixd · GPL-3.0 with additional terms");

    static final String TAGS = pick("Фильтр хештегов", "Фільтр хештегів", "Hashtag filter");

    static final String TAGS_NOTE = pick(
            "Посты с этими хештегами не попадут в рекомендации. Свои посты не прячутся.",
            "Пости з цими хештегами не потраплять у рекомендації. Свої пости не ховаються.",
            "Posts with these hashtags stay out of the feed. Your own are never hidden.");

    static final String TAGS_ADD = pick("Добавить хештег", "Додати хештег", "Add a hashtag");
    static final String TAGS_NONE = pick("Пока пусто", "Поки порожньо", "None yet");
    static final String TAGS_FULL = pick("Больше не влезет", "Більше не влізе", "That is enough of those");

    static final String FPS = pick("Частота кадров", "Частота кадрів", "Frame rate");

    static final String FPS_ABOUT = pick(
            "TikTok сам решает, сколько кадров показывать: где-то 60, где-то 120.",
            "TikTok сам вирішує, скільки кадрів показувати: десь 60, десь 120.",
            "TikTok decides how many frames to show: 60 here, 120 there.");

    static final String FPS_AUTO = pick(
            "Пусть решает TikTok", "Хай вирішує TikTok", "Let TikTok decide");

    static final String FPS_LOCKED = pick("Всегда %s", "Завжди %s", "Always %s");

    static final String FPS_UNSUPPORTED = pick(
            "Экран столько не умеет — будет ближайшая",
            "Екран стільки не вміє — буде найближча",
            "The screen cannot do that, so the nearest one instead");

    static final String FONT_NUNITO = "Nunito";
    static final String FONT_SYSTEM = pick("Системный", "Системний", "The system one");
    static final String FONT_SANS = pick("Обычный", "Звичайний", "Sans");
    static final String FONT_SANS_LIGHT = pick("Тонкий", "Тонкий", "Light");
    static final String FONT_SANS_CONDENSED = pick("Узкий", "Вузький", "Condensed");
    static final String FONT_SERIF = pick("С засечками", "Із засічками", "Serif");
    static final String FONT_MONOSPACE = pick("Моноширинный", "Моноширинний", "Monospace");
    static final String FONT_CURSIVE = pick("Рукописный", "Рукописний", "Cursive");
    static final String FONT_FILE = pick("Свой файл", "Свій файл", "A file of your own");

    static final String FONT_PICK = pick(
            "Выбрать .ttf или .otf", "Обрати .ttf або .otf", "Pick a .ttf or .otf");

    static final String FONT_FAILED = pick(
            "Не получилось прочитать шрифт", "Не вдалося прочитати шрифт",
            "That file is not a font this phone can read");

    static final String EMOJI = pick("Шрифт эмодзи", "Шрифт емодзі", "Emoji");

    static final String EMOJI_SYSTEM = pick("Системные", "Системні", "The system ones");
    static final String EMOJI_TWEMOJI = pick("Twemoji", "Twemoji", "Twemoji");
    static final String EMOJI_NOTO = pick("Noto", "Noto", "Noto");
    static final String EMOJI_BLOB = pick("Blobmoji", "Blobmoji", "Blobmoji");
    static final String EMOJI_FILE = pick("Свой файл", "Свій файл", "A file of your own");
    static final String EMOJI_PREVIEW = "😀  🐱  🫶  👩🏽‍💻  🇷🇺  1️⃣";

    static final String EMOJI_NOTE = pick(
            "Все паки уже внутри мода, скачивать ничего не нужно. Нужен Android 10 "
                    + "и выше: ниже него меняются только буквы.",
            "Усі паки вже всередині мода, завантажувати нічого не треба. Потрібен "
                    + "Android 10 і вище: нижче змінюються лише літери.",
            "Every pack is already inside the mod; nothing is fetched. Android 10 "
                    + "and up: below that only the letters change.");

    static final String EMOJI_READY = pick(
            "Готово — перезапусти приложение", "Готово — перезапусти застосунок",
            "Done -- restart the app");

    // ---------------------------------------------------------- the icon

    static final String ICON = pick("Иконка", "Іконка", "The icon");

    static final String ICON_NOTE = pick(
            "Иконки с конкурса. Хочешь, чтобы твоя была здесь — участвуй.",
            "Іконки з конкурсу. Хочеш, щоб твоя була тут — бери участь.",
            "These came from a contest. Enter it if you want yours here.");

    static final String ICON_CONTEST = pick(
            "Участвовать в конкурсе", "Взяти участь у конкурсі", "Enter the contest");

    static final String ICON_DEFAULT = pick("Фиолетовая", "Фіолетова", "Purple");
    static final String ICON_YELLOW = pick("Жёлтая", "Жовта", "Yellow");
    static final String ICON_NEON = pick("Неоновая", "Неонова", "Neon");

    // -------------------------------------------------------- the donation

    static final String DONATE_BANNER = pick(
            "Значок за поддержку", "Значок за підтримку", "A badge for supporting");

    static final String DONATE_BANNER_TEXT = pick(
            "От 250 ₽: значок рядом с ником и доступ в закрытый чат. Значок видят все с TokkiYou.",
            "Від 250 ₽: значок поруч із ніком і доступ до закритого чату. Значок бачать усі з TokkiYou.",
            "From 250 roubles: a badge beside your name and access to the private chat. Everyone on TokkiYou sees the badge.");

    static final String DONATE_BANNER_HOW = pick(
            "После перевода — в сообщения канала: чек и ваш ID аккаунта.",
            "Після переказу — у повідомлення каналу: чек і ваш ID акаунта.",
            "After paying, write to the channel with the receipt and your "
                    + "account ID.");

    static final String DONATE_BANNER_BUTTON = pick(
            "Пожертвовать", "Пожертвувати", "Donate");

    static final String DONATE_BANNER_WRITE = pick(
            "Сообщения каналу", "Повідомлення каналу", "Message the channel");

    static final String DONATE_STEP_PAY = pick(
            "1. Переведите от 250 ₽", "1. Переказати від 250 ₽", "1. Send 250 ₽ or more");

    static final String DONATE_STEP_ID = pick(
            "2. Скопируйте свой ID", "2. Скопіюйте свій ID", "2. Copy your ID");

    static final String DONATE_ID_NOTE = pick(
            "Нажмите на ID, он скопируется. По нему выдаётся значок.",
            "Натисніть на ID, він скопіюється. За ним видається значок.",
            "Tap the ID to copy it. The badge is given to this account.");

    static final String DONATE_STEP_WRITE = pick(
            "3. Напишите в канал", "3. Напишіть у канал", "3. Write to the channel");

    static final String DONATE_WRITE_NOTE = pick(
            "Пришлите чек и ID", "Надішліть чек і ID", "Send the receipt and the ID");

    static final String DONATE_AFTER = pick(
            "Значок выдаётся вручную, обычно в течение дня. Ссылка в закрытый чат придёт вместе с ним.",
            "Значок видається вручну, зазвичай протягом дня. Посилання в закритий чат надійде разом із ним.",
            "The badge is given by hand, usually within a day. The private-chat link comes with it.");

    static final String TIKTOK_FEATURES = pick("Функции TikTok", "Функції TikTok", "TikTok features");
    static final String TIKTOK_FEATURES_NOTE = pick(
            "Это функции самого TikTok, которые он пока включает не всем. Здесь их "
                    + "можно включить себе. Что-то может работать не до конца.",
            "Це функції самого TikTok, які він поки вмикає не всім. Тут їх можна "
                    + "увімкнути собі. Щось може працювати не до кінця.",
            "These are TikTok's own features that it has not switched on for everybody yet. "
                    + "Here you can switch them on for yourself. Some may not fully work.");
    static final String COMMENT_REPOST = pick("Репост комментариев", "Репост коментарів",
            "Repost comments");
    static final String SELF_REPOST = pick("Репост своих видео", "Репост своїх відео",
            "Repost your own videos");
    static final String TRANSLATE_COMMENTS = pick("Перевод всех комментариев сразу", "Переклад усіх коментарів одразу",
            "Translate all comments at once");
    static final String KEEP_BAR = pick("Не прятать нижнюю панель", "Не ховати нижню панель",
            "Keep the bottom bar in sight");
    static final String NEW_PROFILE = pick("Новый вид профиля TikTok", "Новий вигляд профілю TikTok",
            "TikTok's new profile look");

    static final String NAV_CAPSULE = pick("Нижняя панель в стиле Material 3",
            "Нижня панель у стилі Material 3", "Material 3 bottom bar");

    static final String MD3_FEED = pick("Лента в стиле Material 3", "Стрічка у стилі Material 3",
            "Material 3 feed");
    static final String MD3_PROFILE = pick("Профиль в стиле Material 3", "Профіль у стилі Material 3",
            "Material 3 profile");
    static final String MD3_ICONS = pick("Округлые иконки у кнопок видео", "Округлі іконки біля кнопок відео",
            "Rounded icons on a video's buttons");
    static final String MD3_INBOX = pick("Входящие в стиле Material 3", "Вхідні у стилі Material 3",
            "Material 3 inbox");

    static final String TAB_READ_ALL = pick("Прочитать все?", "Прочитати все?", "Mark all as read?");
    static final String TAB_READ_ALL_NOTE = pick(
            "Число на кнопке пропадёт и вернётся, когда придёт что-то новое.",
            "Число на кнопці зникне й повернеться, коли надійде щось нове.",
            "The number on the tab goes away and comes back when something new arrives.");
    static final String TAB_READ_ALL_YES = pick("Прочитать", "Прочитати", "Mark as read");
    static final String TAB_UNREAD_BACK = pick("Вернуть число?", "Повернути число?", "Bring the number back?");
    static final String TAB_UNREAD_BACK_NOTE = pick(
            "Сейчас число на этой кнопке скрыто.", "Зараз число на цій кнопці приховано.",
            "The number on this tab is hidden at the moment.");
    static final String TAB_UNREAD_BACK_YES = pick("Вернуть", "Повернути", "Bring it back");

    static final String AVATAR_SHAPE = pick("Форма аватарок", "Форма аватарок", "Avatar shape");
    static final String SHAPE_CIRCLE = pick("Круг", "Коло", "Circle");
    static final String SHAPE_ROUNDED = pick("Скруглённый", "Заокруглений", "Rounded");
    static final String SHAPE_SQUARE = pick("Квадрат", "Квадрат", "Square");
    static final String SHAPE_COOKIE = pick("Печенье", "Печиво", "Cookie");
    static final String SHAPE_CLOVER = pick("Клевер", "Конюшина", "Clover");
    static final String SHAPE_BURST = pick("Вспышка", "Спалах", "Burst");
    static final String SHAPE_SUN = pick("Солнце", "Сонце", "Sun");
    static final String SHAPE_PENTAGON = pick("Пятиугольник", "П'ятикутник", "Pentagon");
    static final String SHAPE_HEXAGON = pick("Шестиугольник", "Шестикутник", "Hexagon");
    static final String SHAPE_HEART = pick("Сердце", "Серце", "Heart");

    static final String DEP_TITLE = pick("Деп калькулятор мини", "Деп калькулятор міні", "Dep Calculator mini");
    static final String DEP_HINT = pick("Наберите число и нажмите «ДЕП»", "Наберіть число й натисніть «ДЕП»",
            "Type a number and press DEP");
    static final String DEP_SPIN = pick("ДЕП", "ДЕП", "DEP");
    static final String DEP_BALANCE = pick("Баланс", "Баланс", "Balance");
    static final String DEP_DEBT = pick("Долг", "Борг", "Debt");
    static final String DEP_WON = pick("Выигрыш", "Виграш", "Won");
    static final String DEP_LOST = pick("Проигрыш", "Програш", "Lost");
    static final String DEP_LOAN = pick("Денег нет. Банк выдал кредит.", "Грошей немає. Банк видав кредит.",
            "No money left. The bank gave you a loan.");
    static final String DEP_COIN = pick("ДК", "ДК", "DC");
    static final String DEP_BORROW = pick("Кредит", "Кредит", "Loan");
    static final String DEP_REPAY = pick("Погасить", "Погасити", "Repay");
    static final String DEP_REPAID = pick("Погашено", "Погашено", "Repaid");
    static final String DEP_NO_DEBT = pick("Гасить нечего или нечем", "Гасити нічого або нічим",
            "Nothing to repay, or nothing to repay it with");
    static final String DEP_BROKE = pick("Денег нет. Возьмите кредит.", "Грошей немає. Візьміть кредит.",
            "No money left. Take a loan.");
    static final String DEP_PUSH = pick("Ничья, ставка вернулась", "Нічия, ставка повернулася",
            "A draw, the stake is back");
    static final String DEP_HIT = pick("Ещё", "Ще", "Hit");
    static final String DEP_STAND = pick("Хватит", "Досить", "Stand");
    static final String DEP_YOU = pick("Вы", "Ви", "You");
    static final String DEP_DEALER = pick("Дилер", "Дилер", "Dealer");
    static final String DEP_OFFLINE = pick("Сервер не ответил", "Сервер не відповів", "The server did not answer");
    static final String DEP_PROVE = pick("Сначала подтвердите аккаунт", "Спочатку підтвердьте акаунт",
            "Prove the account is yours first");
    static final String DEP_PROVE_SHORT = pick("нужен аккаунт", "потрібен акаунт", "account needed");
    static final String DEP_PROVE_WHY = pick(
            "Деп коины и значок считает сервер, поэтому играть может только подтверждённый аккаунт: "
                    + "так никто не проиграет чужие коины.",
            "Деп коїни й значок рахує сервер, тому грати може лише підтверджений акаунт: "
                    + "так ніхто не програє чужі коїни.",
            "The server counts the dep coins and the badge, so only a proved account can play: "
                    + "that way nobody can lose somebody else's coins.");
    static final String DEP_SLOW = pick("Слишком быстро", "Занадто швидко", "Too fast");
    static final String DEP_OWED_MOST = pick("Больше в долг не дают. Потолок:", "Більше в борг не дають. Стеля:",
            "No more loans. The ceiling is");
    static final String DEP_PAY_FIRST = pick("Сначала погасите долг", "Спочатку погасіть борг", "Repay the debt first");
    static final String DEP_SHORT = pick("Не хватает. Значок стоит", "Не вистачає. Значок коштує",
            "Not enough. The badge costs");
    static final String DEP_HAND_OPEN = pick("Сначала доиграйте раздачу в 21", "Спочатку дограйте роздачу в 21",
            "Finish the hand of twenty-one first");
    static final String DEP_BUY = pick("Значок «Калькуляторщик»", "Значок «Калькуляторник»", "The Calculator badge");
    static final String DEP_BOUGHT = pick("Значок «Калькуляторщик» ваш", "Значок «Калькуляторник» ваш",
            "The Calculator badge is yours");
    static final String DEP_DAILY = pick("Ежедневные", "Щоденні", "Today's");
    static final String SCREAMER_SOUND = pick("Звук скримера", "Звук скримера", "Screamer sound");
    static final String DEP_FLOAT = pick("Слоты поверх ленты", "Слоти поверх стрічки", "Reels over the feed");
    static final String DEP_NOTE = pick(
            "Мини-версия Деп калькулятора. Деп коины игровые, их считает сервер. "
                    + "Каждый день дают ещё, а за очень много можно купить значок.",
            "Міні-версія Деп калькулятора. Деп коїни ігрові, їх рахує сервер. "
                    + "Щодня дають ще, а за дуже багато можна купити значок.",
            "A small Dep Calculator. Dep coins are play money counted by the server. "
                    + "More arrive each day, and a great many of them buy a badge.");
    static final String CHIP_TEMPORARY = pick("ВРЕМЕННО", "ТИМЧАСОВО", "FOR NOW");
    static final String CHIP_COLLAB = pick("КОЛЛАБ", "КОЛАБ", "COLLAB");

    static final String SCREAMERS_NOTE = pick(
            "Иногда на весь экран выскакивает скример. Громкость зависит от настроек звука.",
            "Іноді на весь екран з’являється скример. Гучність залежить від налаштувань звуку.",
            "An occasional full-screen screamer. Its volume follows your media setting.");
    static final String SCREAMER_NOW = pick("Показать сейчас", "Показати зараз", "Show one now");

    static final String DEP_FULL = pick("Полная версия: zenusus.sbs/dep", "Повна версія: zenusus.sbs/dep",
            "The full thing: zenusus.sbs/dep");

    static final String DEV_FLAGS = pick("Флаги TikTok", "Прапорці TikTok", "TikTok flags");
    static final String DEV_FLAGS_NOTE = pick("Все флаги и их значения", "Усі прапорці та їхні значення",
            "Every flag and its value");
    static final String FLAGS_WARNING = pick(
            "Экспериментально. Флаги управляют внутренностями TikTok: от изменения что-то "
                    + "может сломаться, вылетать или не измениться вообще. Если TikTok три раза "
                    + "подряд не сможет запуститься, ваши изменения отключатся сами.",
            "Експериментально. Прапорці керують нутрощами TikTok: від зміни щось може "
                    + "зламатися, вилітати або не змінитися взагалі. Якщо TikTok тричі поспіль "
                    + "не зможе запуститися, ваші зміни вимкнуться самі.",
            "Experimental. Flags steer the insides of TikTok: changing one can break something, "
                    + "crash the app, or do nothing at all. If TikTok fails to start three times "
                    + "in a row, your changes switch themselves off.");
    static final String FLAGS_HELD = pick(
            "Ваши изменения отключены: TikTok три раза подряд не смог с ними запуститься.",
            "Ваші зміни вимкнено: TikTok тричі поспіль не зміг із ними запуститися.",
            "Your changes are switched off: TikTok failed to start with them three times in a row.");
    static final String FLAGS_AGAIN = pick("Включить снова", "Увімкнути знову", "Switch them back on");
    static final String FLAGS_SEARCH = pick("Часть названия флага", "Частина назви прапорця",
            "Part of a flag's name");
    static final String FLAGS_LOADING = pick("Читаю список флагов…", "Читаю список прапорців…",
            "Reading the list of flags…");
    static final String FLAGS_NO_LIST = pick("В этой версии TikTok список флагов не нашёлся.",
            "У цій версії TikTok список прапорців не знайшовся.",
            "The list of flags was not found in this version of TikTok.");
    static final String FLAGS_CHANGED = pick("Изменённые", "Змінені", "Changed");
    static final String FLAGS_NONE_CHANGED = pick(
            "Пока ничего не изменено. Всего флагов: %d. Найдите нужный по названию.",
            "Поки нічого не змінено. Усього прапорців: %d. Знайдіть потрібний за назвою.",
            "Nothing changed yet. %d flags in all. Find the one you want by name.");
    static final String FLAGS_FOUND = pick("Найдено: %d", "Знайдено: %d", "Found: %d");
    static final String FLAGS_MORE = pick("Показаны первые %d. Уточните название.",
            "Показано перші %d. Уточніть назву.", "The first %d are shown. Narrow the name down.");
    static final String FLAGS_HERE = pick("здесь", "тут", "here");
    static final String FLAGS_AS_TIKTOK = pick("Как у TikTok", "Як у TikTok", "As TikTok has it");
    static final String FLAGS_ON = pick("Включить", "Увімкнути", "On");
    static final String FLAGS_OFF = pick("Выключить", "Вимкнути", "Off");
    static final String FLAGS_RESET = pick("Сбросить", "Скинути", "Reset");
    static final String FLAGS_RESET_ALL = pick("Сбросить все изменения", "Скинути всі зміни",
            "Reset every change");
    static final String FLAGS_BAD = pick("Это не значение такого типа", "Це не значення такого типу",
            "That is not a value of this type");
    static final String FLAGS_FIXED = pick("Флаги такого типа менять нельзя",
            "Прапорці такого типу змінювати не можна", "Flags of this type cannot be changed");

    static final String SPEED_LOCK = pick(
            "Фиксация скорости свайпом вниз", "Фіксація швидкості свайпом униз",
            "Lock the speed by swiping down");

    static final String REMIND_TITLE = pick(
            "Нравится TokkiYou?", "Подобається TokkiYou?", "Enjoying TokkiYou?");

    static final String REMIND_TEXT = pick(
            "Поддержи разработку — значок рядом с ником, градиент на ник "
                    + "и закрытый чат.",
            "Підтримай розробку — значок поруч із ніком, градієнт на нік "
                    + "і закритий чат.",
            "Support the making of it: a badge beside your name, a gradient on "
                    + "the name and the private chat.");

    static final String REMIND_MORE = pick("Подробнее", "Докладніше", "Tell me more");

    static final String REMIND_NEVER = pick(
            "Больше не напоминать", "Більше не нагадувати", "Never remind me");

    static final String STREAK_TEST = pick(
            "Отправить тестовое сообщение", "Надіслати тестове повідомлення",
            "Send a test message");

    static final String STREAK_TEST_NOTE = pick(
            "Всем, у кого есть серия — даже если огонёк горит. Результат в дневнике.",
            "Усім, у кого є серія — навіть якщо вогник горить. Результат у щоденнику.",
            "To everyone with a streak, lit or not. The diary says what happened.");

    static final String STREAK_TEST_GOING = pick(
            "Отправляю, смотри дневник", "Надсилаю, дивись щоденник",
            "Sending; the diary will say");

    static final String STREAK_ALL = pick(
            "Отправить огонёк всем", "Надіслати вогник усім", "Send the flame to everyone");

    static final String STREAK_ALL_NOTE = pick(
            "Выбранный стикер уйдёт всем, с кем у тебя есть серия, прямо сейчас",
            "Вибраний стікер піде всім, з ким у тебе є серія, просто зараз",
            "The chosen sticker goes to everyone you have a streak with, right now");

    static final String STREAK_ALL_ASK = pick(
            "Выбранный стикер будет отправлен всем, с кем у тебя есть серия, — и тем, у кого огонёк уже горит. Это настоящие сообщения.",
            "Вибраний стікер буде надіслано всім, з ким у тебе є серія, — і тим, у кого вогник уже горить. Це справжні повідомлення.",
            "The chosen sticker will be sent to everyone you have a streak with, including lit ones. These are real messages.");

    static final String STREAK_ALL_SEND = pick("Отправить", "Надіслати", "Send");

    static final String STREAK_ALL_NO_STICKER = pick(
            "Сначала выбери стикер для серий (текст пока недоступен)",
            "Спочатку обери стікер для серій (текст поки недоступний)",
            "Choose a streak sticker first (text is not available yet)");

    static final String STREAK_ALL_NO_CHATS = pick(
            "Сначала открой список чатов TikTok, чтобы мод увидел серии",
            "Спочатку відкрий список чатів TikTok, щоб мод побачив серії",
            "Open the TikTok chat list first so the mod can see your streaks");

    static final String STREAK_ALL_NONE = pick(
            "Серий не найдено", "Серій не знайдено", "No streaks found");

    static final String STREAK_ALL_BUSY = pick(
            "Уже отправляю…", "Уже надсилаю…", "Already sending…");

    static final String STREAK_ALL_DONE = pick(
            "Отправлено: %1$d из %2$d", "Надіслано: %1$d з %2$d", "Sent: %1$d of %2$d");

    static final String CAT_FROM = pick("Принёс", "Приніс", "Brought by");

    static final String CAT_YOURS = pick(
            "Хочешь своего кота здесь? Напиши @mrretizixd в Telegram",
            "Хочеш свого кота тут? Напиши @mrretizixd в Telegram",
            "Want your cat here? Write to @mrretizixd on Telegram");

    static final String CAT_WAIT = pick(
            "Коты ещё едут, попробуй ещё раз", "Коти ще їдуть, спробуй ще раз",
            "The cats are still on their way; try again");

    static final String HIDE_LIVE = pick(
            "Скрыть трансляции", "Сховати трансляції", "Hide live rooms");

    static final String HIDE_PHOTOS = pick(
            "Скрыть фото-посты", "Сховати фото-пости", "Hide slideshows");

    static final String DIM = pick(
            "Анти-выгорание", "Анти-вигоряння", "Stop the screen burning in");

    static final String DIM_HOW = pick(
            "Насколько приглушить", "Наскільки приглушити", "How far down");

    static final String DIM_EYE_ON = pick(
            "Прозрачность включена. Нажмите, чтобы выключить",
            "Прозорість увімкнена. Натисніть, щоб вимкнути",
            "Dimming on. Tap to turn off");

    static final String DIM_EYE_OFF = pick(
            "Прозрачность выключена. Нажмите, чтобы включить",
            "Прозорість вимкнена. Натисніть, щоб увімкнути",
            "Dimming off. Tap to turn on");

    static final String DIM_SHIFT = pick(
            "Плавно сдвигать кнопки", "Плавно зсувати кнопки", "Slowly drift controls");

    static final String DIM_SHIFT_SIZE = pick(
            "Насколько сдвигать, dp", "Наскільки зсувати, dp", "How far to drift, dp");

    static final String DIM_NOTE = pick(
            "Приглушение делает панели, кнопки, подпись и индикаторы видео и слайд-шоу тусклее. Фото и видео не меняются. "
                    + "Сдвиг медленно водит те же элементы по небольшой области, чтобы "
                    + "износ не копился на одних и тех же пикселях: движение почти "
                    + "незаметно, а видео не двигается. Сдвиг работает и без приглушения; "
                    + "нажимаются кнопки так же.",
            "Приглушення робить панелі, кнопки, підпис та індикатори відео й слайд-шоу тьмянішими. Фото та відео не змінюються. "
                    + "Зсув повільно водить ті самі елементи по невеликій ділянці, щоб "
                    + "знос не накопичувався на одних і тих самих пікселях: рух майже "
                    + "непомітний, а відео не рухається. Зсув працює і без приглушення; "
                    + "кнопки натискаються так само.",
            "Dimming makes video and slideshow panels, controls, captions and indicators fainter. Photos and videos stay unchanged. "
                    + "Drifting slowly walks the same controls around a small area so "
                    + "wear is not piled on the same pixels: the motion is nearly "
                    + "invisible and the video never moves. Drifting works without "
                    + "dimming, and the controls still work exactly as they did.");

    // ------------------------------------------------------------ own badges

    static final String MINE = pick("Мои значки", "Мої значки", "My badges");

    static final String MINE_NOTE = pick(
            "Порядок и что показывать. Сохраняется на сервере, видно всем.",
            "Порядок і що показувати. Зберігається на сервері, видно всім.",
            "The order, and which to show. Kept on the server, seen by everyone.");

    static final String MINE_NONE = pick(
            "У этого аккаунта пока нет значков", "У цього акаунта поки немає значків",
            "This account has no badges yet");

    static final String MINE_WAIT = pick("Спрашиваю сервер…", "Питаю сервер…",
            "Asking the server...");

    static final String MINE_SAVE = pick(
            "Сохранить значки", "Зберегти значки", "Save my badges");

    static final String MINE_SAVED = pick("Сохранено", "Збережено", "Saved");

    static final String MINE_TOO_OFTEN = pick(
            "Не чаще раза в минуту", "Не частіше разу на хвилину",
            "Once a minute at most");

    static final String FREE_BADGE = pick(
            "Бесплатный значок олда!", "Безкоштовний значок олда!",
            "A free badge for the old lot");

    static final String FREE_BADGE_TEXT = pick(
            "Бирюзовый значок «Использовал TokkiYou до 24-го сентября». "
                    + "После 24-го его больше не выдадут, ни за что.",
            "Бірюзовий значок «Користувався TokkiYou до 24-го вересня». "
                    + "Після 24-го його більше не видадуть, ні за що.",
            "A turquoise badge that says you were here before the 24th of "
                    + "September. After that nobody gets one, at any price.");

    static final String FREE_BADGE_TAKE = pick("Забрать", "Забрати", "Take it");

    static final String FREE_BADGE_GOT = pick(
            "Значок твой", "Значок твій", "It is yours");

    static final String ALWAYS_DATE = pick(
            "Дата под каждым видео", "Дата під кожним відео",
            "The date on every video");

    static final String ALWAYS_DATE_NOTE = pick(
            "Тикток показывает её только если открыть видео с профиля автора.",
            "Тікток показує її лише якщо відкрити відео з профілю автора.",
            "TikTok shows it only when the video was opened from a profile.");

    // ------------------------------------------------------ the plugin store

    static final String STORE = pick("Магазин плагинов", "Магазин плагінів",
            "The plugin store");

    static final String STORE_OPEN = pick("Открыть магазин", "Відкрити магазин",
            "Open the store");

    static final String STORE_WAIT = pick("Смотрю, что есть…", "Дивлюсь, що є…",
            "Looking...");

    static final String STORE_GET = pick("Поставить", "Поставити", "Get it");
    static final String STORE_ANYWAY = pick("Всё равно", "Все одно", "Anyway");
    static final String STORE_GETTING = pick("Качаю плагин", "Завантажую плагін",
            "Fetching the plugin");
    static final String STORE_GOT = pick("Готово, перезапусти приложение",
            "Готово, перезапусти застосунок", "Done -- restart the app");
    static final String STORE_FAILED = pick("Не вышло", "Не вийшло", "That did not work");

    static final String STORE_WRONG_VERSION = pick(
            "Для другой версии TikTok", "Для іншої версії TikTok",
            "For a different TikTok");

    static final String STORE_NOTE = pick(
            "Плагины загружает владелец мода. Ставь только то, чему доверяешь: "
                    + "плагин работает внутри тиктока с твоим аккаунтом.",
            "Плагіни завантажує власник мода. Став лише те, чому довіряєш: "
                    + "плагін працює всередині тіктока з твоїм акаунтом.",
            "Plugins are put there by whoever runs the mod. Install what you trust: "
                    + "a plugin runs inside TikTok with your account.");

    static final String SAVE = pick("Сохранить", "Зберегти", "Save");

    // -------------------------------------------------------- the downloads

    static final String DOWNLOADS = pick("Скачивание", "Завантаження", "Downloads");

    static final String SEARCH = pick("Найти", "Знайти", "Search");
    static final String SEARCH_CLEAR = pick("Очистить", "Очистити", "Clear");
    static final String SEARCH_HINT = pick("Поиск по настройкам", "Пошук у налаштуваннях", "Search settings");
    static final String SEARCH_RESULTS = pick("Результаты поиска", "Результати пошуку", "Search results");
    static final String SEARCH_NONE = pick("Ничего не найдено", "Нічого не знайдено", "Nothing found");
    static final String SEARCH_REGION = pick("Страна и регион", "Країна та регіон", "Country and region");
    static final String SEARCH_ACCENT = pick("Цвет, палитра, сердечко", "Колір, палітра, сердечко", "Colour, palette, heart");
    static final String SEARCH_THEME = pick("Светлая, тёмная и Material-тема", "Світла, темна та Material-тема", "Light, dark and Material theme");
    static final String SEARCH_FEED = pick("Реклама, трансляции и фото-посты", "Реклама, трансляції та фото-пости", "Ads, live rooms and slideshows");
    static final String SEARCH_VIDEO = pick("Звук, перемотка, дата и anti-burn", "Звук, перемотування, дата та anti-burn", "Sound, seek, date and anti-burn");
    static final String SEARCH_DOWNLOADS = pick("Видео, аватар и стикеры", "Відео, аватар і стікери", "Video, avatar and stickers");
    static final String SEARCH_COMMENTS = pick("Копирование целиком и выбор фрагмента", "Копіювання цілком і вибір фрагмента", "Copy all or select a part");
    static final String SEARCH_BACKUP = pick("Импорт, экспорт и сброс", "Імпорт, експорт і скидання", "Import, export and reset");
    static final String SEARCH_TEXTURES = pick("Установить и экспортировать текстурпак", "Встановити й експортувати текстурпак", "Install and export texture pack");
    static final String SEARCH_FONT = pick("Шрифт и эмодзи", "Шрифт та емодзі", "Font and emoji");

    static final String BACKUP = pick("Резервная копия", "Резервна копія", "Backup");
    static final String BACKUP_EXPORT = pick("Экспортировать настройки", "Експортувати налаштування", "Export settings");
    static final String BACKUP_EXPORT_NOTE = pick("JSON без аккаунта, токенов и кэша", "JSON без акаунта, токенів і кешу", "JSON without account, tokens or cache");
    static final String BACKUP_IMPORT = pick("Импортировать настройки", "Імпортувати налаштування", "Import settings");
    static final String BACKUP_IMPORT_NOTE = pick("Сначала покажем, сколько параметров будет изменено", "Спочатку покажемо, скільки параметрів буде змінено", "Shows how many settings will change first");
    static final String BACKUP_APPLY = pick("Применить", "Застосувати", "Apply");
    static final String BACKUP_READING = pick("Читаю резервную копию…", "Читаю резервну копію…", "Reading backup…");
    static final String BACKUP_WRITING = pick("Сохраняю резервную копию…", "Зберігаю резервну копію…", "Writing backup…");
    static final String BACKUP_DONE = pick("Готово", "Готово", "Done");
    static final String BACKUP_FAILED = pick("Не удалось обработать резервную копию", "Не вдалося обробити резервну копію", "Could not process backup");
    static final String BACKUP_RESET = pick("Сбросить категорию", "Скинути категорію", "Reset a category");
    static final String BACKUP_RESET_NOTE = pick("Внешний вид, плеер, лента или все настройки мода", "Вигляд, плеєр, стрічка або всі налаштування мода", "Appearance, player, feed or every mod setting");
    static final String BACKUP_RESET_ASK = pick("Аккаунт, токены, история и кэш не затрагиваются.", "Акаунт, токени, історія та кеш не зачіпаються.", "Account, tokens, history and cache are untouched.");
    static final String RESET_APPEARANCE = pick("Внешний вид", "Вигляд", "Appearance");
    static final String RESET_PLAYER = pick("Плеер и скачивание", "Плеєр і завантаження", "Player and downloads");
    static final String RESET_FEED = pick("Лента и регион", "Стрічка та регіон", "Feed and region");
    static final String RESET_ALL = pick("Все настройки мода", "Усі налаштування мода", "Every mod setting");

    static String backupPreview(int count) {
        return pick("Будут изменены " + count + " настроек. Аккаунт, токены, история и кэш не затрагиваются.",
                "Буде змінено " + count + " налаштувань. Акаунт, токени, історія та кеш не зачіпаються.",
                count + " settings will change. Account, tokens, history and cache are untouched.");
    }

    static final String NO_WATERMARK = pick(
            "Без водяного знака", "Без водяного знака", "Without the watermark");

    static final String DOWNLOAD_ALWAYS = pick(
            "Сохранять можно всё", "Зберігати можна все", "Save anything");

    static final String FEED = pick("Лента", "Стрічка", "Feed");

    static final String HIDE_ADS = pick(
            "Убирать рекламу", "Прибирати рекламу", "Drop the advertisements");

    // --------------------------------------------------------- the plugins

    static final String PLUGINS = pick("Плагины", "Плагіни", "Plugins");

    static final String PLUGIN_INSTALL = pick(
            "Установить плагин", "Встановити плагін", "Install a plugin");

    static final String PLUGIN_INSTALL_NOTE = pick(
            "Файл .mtp", "Файл .mtp", "An .mtp file");

    static final String PLUGIN_NONE = pick(
            "Пока ничего не установлено.",
            "Поки нічого не встановлено.",
            "Nothing installed yet.");

    static final String PLUGIN_WARNING = pick(
            "Песочницы нет. Ставьте только то, чему доверяете.",
            "Пісочниці немає. Встановлюйте лише те, чому довіряєте.",
            "No sandbox. Install only what you trust.");

    static final String PLUGIN_DOCS = pick(
            "Как писать плагины", "Як писати плагіни", "Writing plugins");

    static final String PLUGIN_DOCS_NOTE = pick(
            "Документация на нашем гите", "Документація на нашому гіті",
            "The documentation on our git");

    static final String PLUGIN_PUBLISH_PANEL = pick(
            "В магазин плагины выкладывает автор мода. Пришлите файл в канал.",
            "У магазин плагіни викладає автор мода. Надішліть файл у канал.",
            "The store is filled by the mod's author. Send the file to the channel.");

    static final String PLUGIN_AUDIT_HEAD = pick(
            "Что делает код плагина:", "Що робить код плагіна:", "What the plugin's code does:");

    static final String PLUGIN_AUDIT_CLEAN = pick(
            "Ничего особенного не найдено.", "Нічого особливого не знайдено.",
            "Nothing out of the ordinary.");

    static final String PLUGIN_AUDIT_NOTE = pick(
            "Плагин работает с правами TikTok. Ставьте только то, чему доверяете.",
            "Плагін працює з правами TikTok. Ставте лише те, чому довіряєте.",
            "A plugin runs with TikTok's own rights. Install only what you trust.");

    static final String PLUGIN_REVIEW = pick("Плагин", "Плагін", "Plugin");

    static final String PLUGIN_INSTALLED = pick(
            "Плагин установлен", "Плагін встановлено", "Plugin installed");

    static final String PLUGIN_REMOVE = pick("Удалить", "Видалити", "Remove");

    static final String PLUGIN_REMOVE_ASK = pick(
            "Удалить плагин?", "Видалити плагін?", "Remove the plugin?");

    static final String CANCEL = pick("Отмена", "Скасувати", "Cancel");

    static final String PLUGIN_HOLD = pick(
            "Долгое нажатие — удалить",
            "Довге натискання — видалити",
            "Hold to remove");

    // ----------------------------------------------------------- the links

    static final String LINKS = pick("Ссылки", "Посилання", "Links");

    static final String CHANNEL = pick("Канал", "Канал", "Channel");

    static final String FORUM = pick("Форум", "Форум", "Forum");

    // ----------------------------------------------------------- the streaks

    static final String STREAKS = pick("Серии", "Серії", "Streaks");

    static final String STREAK_AUTO = pick(
            "Продлевать серии сами", "Продовжувати серії самі", "Keep streaks alive");

    static final String STREAK_HEALTH = pick(
            "Готовность автопродления", "Готовність автопродовження", "Autoprodlenie readiness");

    static final String STREAK_HEALTH_FIX = pick(
            "Нажмите, чтобы разрешить точный будильник и снять экономию батареи",
            "Натисніть, щоб дозволити точний будильник і зняти економію батареї",
            "Tap to allow exact alarms and lift the battery saver");

    static final String BETA = pick("бета", "бета", "beta");

    static final String STREAK_NOTE = pick(
            "Отправляет выбранный стикер тем, с кем серия вот-вот погаснет. "
                    + "Не чаще раза в сутки на человека.",
            "Надсилає вибраний стікер тим, з ким серія ось-ось згасне. "
                    + "Не частіше разу на добу на людину.",
            "Sends the sticker you picked to whoever the streak is about to lapse "
                    + "with, at most once a day each.");

    static final String STREAK_STICKER = pick(
            "Чем продлевать", "Чим продовжувати", "What to send");

    static final String STREAK_NOTHING = pick(
            "Избранные стикеры загружаются из TikTok",
            "Обрані стікери завантажуються з TikTok",
            "Favourite stickers load from TikTok");

    // ------------------------------------------------------- the updates

    static final String UPDATE = pick("Обновление", "Оновлення", "An update");

    static final String UPDATE_THERE_IS = pick(
            "Вышла версия", "Вийшла версія", "There is a version");
    static final String UPDATE_FEATURES = pick("Что нового:", "Що нового:", "What's new:");

    static final String UPDATE_GET = pick("Скачать", "Завантажити", "Get it");

    static final String UPDATE_LATER = pick("Назад", "Назад", "Back");

    static final String UPDATE_NEVER = pick(
            "Больше не напоминать", "Більше не нагадувати", "Stop reminding me");

    static final String UPDATE_GETTING = pick("Скачиваю", "Завантажую", "Getting it");

    static final String UPDATE_FAILED = pick(
            "Не получилось скачать", "Не вдалося завантажити", "Could not get it");

    static final String UPDATE_NONE = pick(
            "Уже последняя версия", "Вже остання версія", "This is the latest");

    static final String UPDATE_NO_ANSWER = pick(
            "Сервер не ответил", "Сервер не відповів", "The server did not answer");

    static final String UPDATE_ALLOW = pick(
            "Разрешите установку из этого источника, и я поставлю",
            "Дозвольте встановлення з цього джерела, і я поставлю",
            "Allow installing from this source and it will go on");

    static final String UPDATE_CHECK = pick(
            "Проверить обновления", "Перевірити оновлення", "Check for updates");

    static final String UPDATE_REMIND = pick(
            "Напоминать об обновлениях", "Нагадувати про оновлення",
            "Remind me about updates");

    static final String UPDATE_INSTALL = pick(
            "Установить скачанное", "Встановити завантажене", "Install what was downloaded");

    static final String VERSIONS = pick("Версии", "Версії", "Versions");

    static final String SOURCE = pick("Исходники", "Вихідники", "The source");


    static final String CHANNEL_TITLE = pick("Канал", "Канал", "Channel");

    static final String CHANNEL_NEWS_A = pick("Новости/", "Новини/", "News/");

    static final String CHANNEL_NEWS_B = pick("обновления", "оновлення", "updates");

    static final String CHANNEL_SUBSCRIBE = pick("Подписаться", "Підписатися", "Subscribe");

    static final String THANKS = pick("Благодарности", "Подяки", "Thanks");

    static final String THANKS_NOTE = pick(
            "Люди, без которых мода бы не было.",
            "Люди, без яких мода б не було.",
            "The people the mod would not exist without.");

    static final String THANKS_OWNER = pick(
            "Владелец мода", "Власник мода", "The mod's owner");

    static final String THANKS_AI = pick(
            "Помог с разработкой",
            "Допоміг із розробкою",
            "Helped with development");

    static final String BADGES = pick("Значки", "Значки", "Badges");

    static final String SEARCH_BADGES = pick(
            "значок значки порядок подтверждение аккаунт",
            "значок значки порядок підтвердження акаунт",
            "badge badges order proof account");

    static final String PROVE_AUTO_GOING = pick(
            "Ставлю код в описание и жду сервер. Около минуты.",
            "Ставлю код в опис і чекаю сервер. Близько хвилини.",
            "Putting the code in your bio and waiting for the server. About a minute.");

    static final String PROVE_AUTO_NO_ROOM = pick(
            "В описании не хватает места для кода. Подтверди вручную.",
            "В описі бракує місця для коду. Підтвердь вручну.",
            "There is no room in the bio for the code. Do it by hand.");

    static final String PROVE_AUTO_BUSY = pick(
            "Проверка уже идёт", "Перевірка вже триває", "Already checking");

    static final String PROVE_AUTO_STOPPED = pick(
            "Само не получилось. Прежнее описание на месте.",
            "Само не вийшло. Попередній опис на місці.",
            "It could not be done for you. Your bio is as it was.");

    static final String PROVE_AUTO_NO_CODE = pick(
            "Сервер не выдал код", "Сервер не видав код", "The server gave no code");

    static final String PROVE_AUTO_NOT_SAVED = pick(
            "TikTok не сохранил описание",
            "TikTok не зберіг опис",
            "TikTok did not save the bio");

    static final String PROVE_AUTO_CHANGED = pick(
            "Описание поменялось по ходу, я его не трогаю",
            "Опис змінився в процесі, я його не чіпаю",
            "The bio changed meanwhile, so it is left alone");

    static final String PROVE_AUTO_NOT_FOUND = pick(
            "Сервер не увидел код в профиле. Описание вернул.",
            "Сервер не побачив код у профілі. Опис повернув.",
            "The server did not see the code on the profile. The bio is back.");

    static final String DONATE = pick("Поддержать", "Підтримати", "Support the project");

    static final String DONATE_NOTE = pick(
            "Каждый ваш рубль помогает держать проект на плаву и развивать открытое "
                    + "сообщество моддинга. Спасибо.",
            "Кожен ваш рубль допомагає тримати проєкт на плаву та розвивати відкриту "
                    + "спільноту модингу. Дякуємо.",
            "Every rouble keeps the project afloat and goes back into the open source "
                    + "modding community. Thank you.");

    static final String YOOMONEY = pick("ЮMoney", "ЮMoney", "YooMoney");

    static final String YOOMONEY_NOTE = pick(
            "Перевод напрямую", "Переказ напряму", "Straight to the transfer page");

    static final String NO_BROWSER = pick(
            "Нечем открыть ссылку", "Нема чим відкрити посилання", "Nothing here opens links");

    static final String DIARY_TITLE = pick("Журнал мода", "Журнал мода", "The mod's diary");

    static final String COPY = pick("Скопировать", "Скопіювати", "Copy");

    static final String COPIED = pick("Скопировано", "Скопійовано", "Copied");

    static final String CLEAR = pick("Очистить", "Очистити", "Clear");

    static final String DIARY = pick(
            "Что мод видел", "Що мод бачив", "What the mod saw");

    static final String CLEARED = pick("Журнал очищен", "Журнал очищено", "The diary is empty");

    static final String ACCOUNT = pick("Аккаунт", "Акаунт", "Account");

    static final String ACCOUNT_ID = pick("ID", "ID", "ID");

    static final String ACCOUNT_SEC_ID = pick(
            "ID для ссылки", "ID для посилання", "The id a link is built from");

    static final String ACCOUNT_UNKNOWN = pick(
            "Пока неизвестен", "Поки невідомий", "Not seen yet");

    private static String language() {
        try {
            return Locale.getDefault().getLanguage();
        } catch (Throwable ignored) {
            return "en";
        }
    }

    static final String DEV_ON = pick("Режим разработчика включён",
            "Режим розробника увімкнено", "Developer mode is on");
    static final String DEV_ALREADY = pick("Режим разработчика уже включён",
            "Режим розробника вже увімкнено", "Developer mode is already on");
    static final String DEV_SWITCH = pick("Режим разработчика",
            "Режим розробника", "Developer mode");
    static final String DEV_FULL_REPORT = pick("Скопировать полный отчёт",
            "Скопіювати повний звіт", "Copy the full report");
    static final String DEV_FULL_NOTE = pick(
            "Устройство, сборка, хуки, плагины и весь журнал. Без id аккаунта и токенов.",
            "Пристрій, збірка, хуки, плагіни та весь журнал. Без id акаунта й токенів.",
            "Device, build, hooks, plugins and the whole diary. No account id or tokens.");

    // Profile appearance: shorter cards.
    static final String PROFILE_LOOK = pick("Вид ника", "Вигляд імені", "Nickname look");
    static final String PROFILE_SYNC = pick("Синхронизация", "Синхронізація", "Sync");
    static final String PROFILE_FINE = pick("Тонкая настройка", "Тонке налаштування", "Fine tuning");

    // Redesigned sheets: second lines under the menu rows.
    static final String SHEET_DL_HINT = pick("Чем выше качество, тем больше файл", "Чим вища якість, тим більший файл", "Higher quality means a bigger file");
    static final String ACT_DOWNLOAD_HINT = pick("Видео, фото или только звук", "Відео, фото або лише звук", "Video, photo or audio only");
    static final String ACT_PLAYBACK_HINT = pick("Скорость и перемотка", "Швидкість і перемотування", "Speed and seeking");
    static final String ACT_FRAME_HINT = pick("Текущий момент как фото", "Поточний момент як фото", "The current moment as a photo");
    static final String ACT_HIDE = pick("Скрыть из ленты", "Приховати зі стрічки", "Hide from feed");
    static final String ACT_HIDE_HINT = pick("Автор или подборка", "Автор або добірка", "Author or suggestion");
    static final String ACT_PLAYBACK = pick("Воспроизведение", "Відтворення", "Playback");
    static final String ACT_DOWNLOAD = pick("Скачать", "Завантажити", "Download");
    static final String SHEET_PICK_QUALITY = pick("Выберите качество", "Оберіть якість", "Choose quality");
    static final String SHEET_BEST = pick("Лучшее качество", "Найкраща якість", "Best quality");
    static final String SHEET_WORST = pick("Худшее качество", "Найгірша якість", "Lowest quality");
    static final String SHEET_PHOTOS = pick("Фото", "Фото", "Photos");
    static final String SHEET_POST_TITLE = pick("Скачать публикацию", "Завантажити публікацію", "Download post");
    static String allPhotos(int count) {
        return pick("Скачать все фото (" + count + ")", "Завантажити всі фото (" + count + ")", "Download all photos (" + count + ")");
    }
    static String photosSaved(int saved, int total) {
        return pick("Сохранено " + saved + " из " + total + " фото", "Збережено " + saved + " із " + total + " фото", "Saved " + saved + " of " + total + " photos");
    }
    static final String SHEET_AUDIO = pick("Скачать аудио", "Завантажити аудіо", "Download audio");
    static final String SHEET_ONE_SIZE = pick("Для этого видео TikTok отдаёт только одно качество", "Для цього відео TikTok віддає лише одну якість", "TikTok offers only one quality for this video");

    // First-run question about crash reports.
    static final String CONSENT_TITLE = pick("Помочь исправлять сбои?", "Допомогти виправляти збої?", "Help fix crashes?");
    static final String CONSENT_BODY = pick(
            "Если TokkiYou вылетит, он отправит разработчику технический след сбоя: версию мода и Android, список вызовов. Без сообщений, аккаунта и личных данных.",
            "Якщо TokkiYou вилетить, він надішле розробнику технічний слід збою: версію мода й Android, список викликів. Без повідомлень, акаунта й особистих даних.",
            "If TokkiYou crashes, it sends the developer a technical trace: the mod and Android versions and the call list. No messages, account or personal data.");
    static final String CONSENT_NOTE = pick(
            "Этот выбор относится только к техническим отчётам о сбоях.",
            "Цей вибір стосується лише технічних звітів про збої.",
            "This choice only concerns technical crash reports.");
    static final String CONSENT_WAIT = pick("Ответить можно через %d с", "Відповісти можна за %d с", "You can answer in %d s");
    static final String CONSENT_YES = pick("Да, отправлять", "Так, надсилати", "Yes, send them");
    static final String CONSENT_NO = pick("Нет", "Ні", "No");

    // The walkthrough.
    static final String TUTORIAL_NEXT = pick("Далее", "Далі", "Next");
    static final String TUTORIAL_BACK = pick("Назад", "Назад", "Back");
    static final String TUTORIAL_DONE = pick("Готово", "Готово", "Done");
    static final String TUTORIAL_SKIP = pick("Пропустить", "Пропустити", "Skip");
    static final String[] TUTORIAL_TITLES = {
            pick("Добро пожаловать в TokkiYou", "Ласкаво просимо в TokkiYou", "Welcome to TokkiYou"),
            pick("Своя палитра", "Своя палітра", "Your own palette"),
            pick("Защита экрана", "Захист екрана", "Screen protection"),
            pick("Всё под рукой", "Все під рукою", "Everything at hand")};
    static final String[] TUTORIAL_WORDS = {
            pick("Все функции мода собраны в одном месте: профиль → меню → настройки TikTok → TokkiYou.",
                    "Усі функції мода зібрано в одному місці: профіль → меню → налаштування TikTok → TokkiYou.",
                    "Every feature of the mod lives in one place: profile → menu → TikTok settings → TokkiYou."),
            pick("Откройте «Внешний вид» → «Акцент». Круг — доступный цвет, квадрат — выбранный.",
                    "Відкрийте «Зовнішній вигляд» → «Акцент». Коло — доступний колір, квадрат — вибраний.",
                    "Open Appearance → Accent. A circle is an available colour, a square is the chosen one."),
            pick("Кнопка с глазом в ленте делает кнопки прозрачными. Закрытый глаз — защита включена. Видео и фото не меняются, а сердце двойного тапа остаётся ярким.",
                    "Кнопка з оком у стрічці робить кнопки прозорими. Заплющене око — захист увімкнено. Відео та фото не змінюються, а серце подвійного дотику лишається яскравим.",
                    "The eye button in the feed makes the buttons transparent. A closed eye means protection is on. Videos and photos stay as they are, and the double-tap heart stays bright."),
            pick("Прозрачность меняется сразу. Если другой настройке нужен перезапуск, снизу появится кнопка. Это обучение можно открыть снова в TokkiYou.",
                    "Прозорість змінюється одразу. Якщо іншому налаштуванню потрібен перезапуск, знизу з’явиться кнопка. Це навчання можна відкрити знову в TokkiYou.",
                    "Transparency changes at once. When another setting needs a restart, a button appears at the bottom. You can reopen this tour in TokkiYou.")};

    /** 0 for Russian, 1 for Ukrainian, 2 for everything else: for {@link Local}. */
    static int language3() {
        return RU ? 0 : UK ? 1 : 2;
    }

    private static String pick(String russian, String ukrainian, String english) {
        if (RU) return russian;
        if (UK) return ukrainian;
        return english;
    }
}
