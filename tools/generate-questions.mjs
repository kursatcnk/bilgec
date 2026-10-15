import fs from "node:fs";
import path from "node:path";
import initSqlJs from "sql.js";
import sharp from "sharp";
import { manualQuestions } from "./manual-questions.mjs";

const root = path.resolve(import.meta.dirname, "..");
const countries = JSON.parse(fs.readFileSync(path.join(root, "data/sources/countries.json"), "utf8"))
  .filter((country) => country.unMember || ["PS", "VA"].includes(country.cca2))
  .sort((a, b) => a.cca2.localeCompare(b.cca2));
const elements = JSON.parse(fs.readFileSync(path.join(root, "data/sources/periodic-table.json"), "utf8"))
  .elements.filter((element) => element.number <= 118);

if (countries.length !== 195) throw new Error(`195 ülke bekleniyordu, ${countries.length} bulundu.`);
if (elements.length !== 118) throw new Error(`118 element bekleniyordu, ${elements.length} bulundu.`);
if (manualQuestions.length < 100) throw new Error(`En az 100 elle hazırlanmış soru bekleniyordu, ${manualQuestions.length} bulundu.`);

const capitalNames = {
  "Abu Dhabi": "Abu Dabi", "Athens": "Atina", "Baghdad": "Bağdat", "Beijing": "Pekin",
  "Belgrade": "Belgrad", "Brussels": "Brüksel", "Bucharest": "Bükreş", "Cairo": "Kahire",
  "Copenhagen": "Kopenhag", "Damascus": "Şam", "Helsinki": "Helsinki", "Lisbon": "Lizbon",
  "London": "Londra", "Moscow": "Moskova", "Nicosia": "Lefkoşa", "Prague": "Prag",
  "Rome": "Roma", "Sofia": "Sofya", "Tbilisi": "Tiflis", "Tehran": "Tahran",
  "Vienna": "Viyana", "Warsaw": "Varşova", "Yerevan": "Erivan",
};
const continentNames = { Africa: "Afrika", Americas: "Amerika", Asia: "Asya", Europe: "Avrupa", Oceania: "Okyanusya", Antarctic: "Antarktika" };
const elementNames = [
  "Hidrojen", "Helyum", "Lityum", "Berilyum", "Bor", "Karbon", "Azot", "Oksijen", "Flor", "Neon",
  "Sodyum", "Magnezyum", "Alüminyum", "Silisyum", "Fosfor", "Kükürt", "Klor", "Argon", "Potasyum", "Kalsiyum",
  "Skandiyum", "Titanyum", "Vanadyum", "Krom", "Manganez", "Demir", "Kobalt", "Nikel", "Bakır", "Çinko",
  "Galyum", "Germanyum", "Arsenik", "Selenyum", "Brom", "Kripton", "Rubidyum", "Stronsiyum", "İtriyum", "Zirkonyum",
  "Niyobyum", "Molibden", "Teknesyum", "Rutenyum", "Rodyum", "Paladyum", "Gümüş", "Kadmiyum", "İndiyum", "Kalay",
  "Antimon", "Tellür", "İyot", "Ksenon", "Sezyum", "Baryum", "Lantan", "Seryum", "Praseodim", "Neodim",
  "Prometyum", "Samaryum", "Evropiyum", "Gadolinyum", "Terbiyum", "Disprozyum", "Holmiyum", "Erbiyum", "Tulyum", "İterbiyum",
  "Lutesyum", "Hafniyum", "Tantal", "Tungsten", "Renyum", "Osmiyum", "İridyum", "Platin", "Altın", "Cıva",
  "Talyum", "Kurşun", "Bizmut", "Polonyum", "Astatin", "Radon", "Fransiyum", "Radyum", "Aktinyum", "Toryum",
  "Protaktinyum", "Uranyum", "Neptünyum", "Plütonyum", "Amerikyum", "Küriyum", "Berkelyum", "Kaliforniyum", "Einsteinyum", "Fermiyum",
  "Mendelevyum", "Nobelyum", "Lavrensiyum", "Rutherfordiyum", "Dubniyum", "Seaborgiyum", "Bohriyum", "Hassiyum", "Meitneriyum", "Darmstadtiyum",
  "Röntgenyum", "Kopernikyum", "Nihonyum", "Flerovyum", "Moskovyum", "Livermoryum", "Tennesin", "Oganesson",
];

const upperFirst = (value) => value ? `${value[0].toLocaleUpperCase("tr-TR")}${value.slice(1)}` : value;
const countryName = (country) => upperFirst(country.translations?.tur?.common || country.name.common);
const capitalName = (country) => capitalNames[country.capital?.[0]] || country.capital?.[0];
const continentName = (country) => continentNames[country.region] || country.region;
const commonCountryCodes = new Set([
  "TR", "DE", "FR", "GB", "IT", "ES", "PT", "NL", "BE", "CH", "AT", "SE", "NO", "DK", "FI", "IS", "IE", "PL", "CZ", "SK",
  "HU", "RO", "BG", "GR", "RS", "HR", "BA", "AL", "MK", "SI", "UA", "RU", "BY", "LT", "LV", "EE", "MD", "CY", "MT", "LU", "VA",
  "AZ", "GE", "AM", "IR", "IQ", "SY", "IL", "PS", "JO", "LB", "SA", "AE", "QA", "KW", "EG", "KZ",
  "CN", "JP", "KR", "KP", "IN", "PK", "BD", "ID", "MY", "SG", "TH", "VN", "PH", "NP", "AF", "MN", "LK",
  "US", "CA", "MX", "BR", "AR", "CL", "CO", "PE", "VE", "CU", "JM",
  "ZA", "MA", "DZ", "TN", "LY", "ET", "KE", "NG", "GH", "SD", "SO", "TZ",
  "AU", "NZ",
]);
const quizCountries = countries.filter((country) => commonCountryCodes.has(country.cca2));
const countryByCode = new Map(quizCountries.map((country) => [country.cca3, country]));

let nextId = 1;
const questions = [];
function hash(text) {
  let value = 2166136261;
  for (const char of text) value = Math.imul(value ^ char.charCodeAt(0), 16777619);
  return value >>> 0;
}
function shuffled(values, seed) {
  const copy = [...new Set(values)];
  let state = hash(seed) || 1;
  for (let index = copy.length - 1; index > 0; index--) {
    state = (Math.imul(state, 1664525) + 1013904223) >>> 0;
    const target = state % (index + 1);
    [copy[index], copy[target]] = [copy[target], copy[index]];
  }
  return copy;
}
function addQuestion({ category, prompt, correct, wrong, explanation, factKey, imageAsset = null, reviewed = true }) {
  const options = shuffled([correct, ...wrong.filter((item) => item !== correct)], `${factKey}:${prompt}`).slice(0, 4);
  if (options.length !== 4) throw new Error(`Dört benzersiz seçenek üretilemedi: ${prompt}`);
  questions.push({
    id: nextId++, category, prompt, options, correctIndex: options.indexOf(correct), explanation,
    factKey, imageAsset, reviewed,
  });
}
function wrongFrom(values, correct, seed, forbidden = []) {
  return shuffled(values.filter((value) => value && value !== correct && !forbidden.includes(value)), seed).slice(0, 3);
}

const names = quizCountries.map(countryName);
const capitals = quizCountries.map(capitalName).filter(Boolean);
const continents = [...new Set(quizCountries.map(continentName))];
const currencyNames = {
  USD: "ABD doları", EUR: "Euro", GBP: "İngiliz sterlini", JPY: "Japon yeni",
  CNY: "Çin yuanı", TRY: "Türk lirası", RUB: "Rus rublesi", INR: "Hindistan rupisi",
  CAD: "Kanada doları", AUD: "Avustralya doları", NZD: "Yeni Zelanda doları",
  BRL: "Brezilya reali", MXN: "Meksika pesosu", ARS: "Arjantin pesosu",
  CLP: "Şili pesosu", COP: "Kolombiya pesosu", CHF: "İsviçre frangı",
  SEK: "İsveç kronu", NOK: "Norveç kronu", DKK: "Danimarka kronu",
  PLN: "Polonya zlotisi", CZK: "Çek korunası", HUF: "Macar forinti",
  RON: "Rumen leyi", BGN: "Bulgar levası", UAH: "Ukrayna grivnası",
  KRW: "Güney Kore wonu", IDR: "Endonezya rupiahı", MYR: "Malezya ringgiti",
  SGD: "Singapur doları", THB: "Tayland bahtı", VND: "Vietnam dongu",
  PHP: "Filipin pesosu", PKR: "Pakistan rupisi", BDT: "Bangladeş takası",
  SAR: "Suudi Arabistan riyali", AED: "BAE dirhemi", ILS: "İsrail şekeli",
  EGP: "Mısır lirası", ZAR: "Güney Afrika randı", NGN: "Nijerya nairası",
  MAD: "Fas dirhemi", DZD: "Cezayir dinarı", KES: "Kenya şilini",
};
const currencyChoices = [...new Set(Object.values(currencyNames))];

for (const country of quizCountries) {
  const name = countryName(country);
  const continent = continentName(country);
  const regionalCountries = quizCountries.filter((item) => item.region === country.region).map(countryName);
  const flagWrong = wrongFrom(regionalCountries.length >= 4 ? regionalCountries : names, name, `flag:${country.cca2}`);
  for (const prompt of [
    "Görseldeki bayrak hangi ülkeye aittir?",
    "Bu bayrak hangi ülkeyi temsil eder?",
    "Ekrandaki bayrağın ülkesi hangisidir?",
    "Bu ulusal bayrak hangi seçenekte doğru verilmiştir?",
    "Gösterilen bayrağı kullanan ülke hangisidir?",
    "Bu bayrak hangi ülkenin ulusal simgesidir?",
  ]) {
    addQuestion({ category: "Bayraklar", prompt, correct: name, wrong: flagWrong, explanation: `Görselde ${name} bayrağı yer alıyor.`, factKey: `flag:${country.cca2}`, imageAsset: `flags/${country.cca2.toLowerCase()}.png` });
  }

  const capital = capitalName(country);
  if (capital) {
    const regionalCapitals = quizCountries.filter((item) => item.region === country.region).map(capitalName).filter(Boolean);
    const capitalWrong = wrongFrom(regionalCapitals.length >= 4 ? regionalCapitals : capitals, capital, `capital:${country.cca2}`);
    addQuestion({ category: "Coğrafya", prompt: `${name} ülkesinin başkenti hangisidir?`, correct: capital, wrong: capitalWrong, explanation: `${name} ülkesinin başkenti ${capital} şehridir.`, factKey: `capital:${country.cca2}` });
    addQuestion({ category: "Coğrafya", prompt: `${name} için doğru başkent hangisidir?`, correct: capital, wrong: capitalWrong, explanation: `${capital}, ${name} ülkesinin başkentidir.`, factKey: `capital:${country.cca2}` });
    addQuestion({ category: "Coğrafya", prompt: `${capital} hangi ülkenin başkentidir?`, correct: name, wrong: flagWrong, explanation: `${capital}, ${name} ülkesinin başkentidir.`, factKey: `capital:${country.cca2}` });
    addQuestion({ category: "Coğrafya", prompt: `Başkenti ${capital} olan ülke hangisidir?`, correct: name, wrong: flagWrong, explanation: `${capital}, ${name} ülkesinin başkentidir.`, factKey: `capital:${country.cca2}` });
    addQuestion({ category: "Coğrafya", prompt: `${name} denince hangi başkent eşleştirmesi doğrudur?`, correct: capital, wrong: capitalWrong, explanation: `${name} ile ${capital} doğru ülke-başkent eşleşmesidir.`, factKey: `capital:${country.cca2}` });
    addQuestion({ category: "Coğrafya", prompt: `Aşağıdaki şehirlerden hangisi ${name} ülkesinin yönetim merkezidir?`, correct: capital, wrong: capitalWrong, explanation: `${name} ülkesinin başkenti ${capital} şehridir.`, factKey: `capital:${country.cca2}` });
    addQuestion({ category: "Coğrafya", prompt: `${name} ülkesinin yönetim merkezi hangi şehirdir?`, correct: capital, wrong: capitalWrong, explanation: `${name} ülkesinin başkenti ${capital} şehridir.`, factKey: `capital:${country.cca2}` });
    addQuestion({ category: "Coğrafya", prompt: `${name} ülkesinin başkentini seçin.`, correct: capital, wrong: capitalWrong, explanation: `${capital}, ${name} ülkesinin başkentidir.`, factKey: `capital:${country.cca2}` });
  }

  const continentWrong = wrongFrom(continents, continent, `continent:${country.cca2}`);
  for (const prompt of [
    `${name} hangi kıtadadır?`,
    `${name} ülkesinin bulunduğu kıta hangisidir?`,
    `${name} hangi kıta ile doğru eşleştirilir?`,
    `Haritada ${name} ülkesini ararken hangi kıtaya bakmalısınız?`,
    `${name} hangi kıtada yer alır?`,
    `${name} için doğru kıta hangisidir?`,
  ]) {
    addQuestion({ category: "Coğrafya", prompt, correct: continent, wrong: continentWrong, explanation: `${name}, ${continent} kıtasında yer alır.`, factKey: `continent:${country.cca2}` });
  }

  const currencyCode = Object.keys(country.currencies || {})[0];
  const currency = currencyNames[currencyCode];
  if (currency) {
    const wrong = wrongFrom(currencyChoices, currency, `currency:${country.cca2}`);
    for (const prompt of [
      `${name} ülkesinde kullanılan para birimi hangisidir?`,
      `${name} hangi para birimini kullanır?`,
      `Aşağıdaki para birimlerinden hangisi ${name} ile eşleşir?`,
      `${name} ülkesine seyahat ederken hangi para birimi gerekir?`,
    ]) {
      addQuestion({ category: "Coğrafya", prompt, correct: currency, wrong, explanation: `${name} ülkesinde ${currency} kullanılır.`, factKey: `currency:${country.cca2}` });
    }
  }

  for (const borderCode of country.borders || []) {
    const border = countryByCode.get(borderCode);
    if (!border) continue;
    const correct = countryName(border);
    const forbidden = (country.borders || []).map((code) => countryByCode.get(code)).filter(Boolean).map(countryName);
    const wrong = wrongFrom(regionalCountries, correct, `border:${country.cca2}:${borderCode}`, forbidden);
    if (wrong.length === 3) {
      for (const prompt of [
        `Hangisi ${name} ile kara sınırı olan ülkelerden biridir?`,
        `${name} ülkesinin kara komşularından biri hangisidir?`,
      ]) {
        addQuestion({ category: "Coğrafya", prompt, correct, wrong, explanation: `${name} ile ${correct} arasında kara sınırı bulunur.`, factKey: `border:${country.cca2}:${borderCode}` });
      }
    }
  }
}

const commonElementNumbers = new Set([1, 2, 3, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 26, 29, 30, 47, 50, 53, 74, 78, 79, 80, 82, 92]);
for (const element of elements.filter((item) => commonElementNumbers.has(item.number))) {
  const name = elementNames[element.number - 1];
  const nearby = elements.filter((item) => Math.abs(item.number - element.number) <= 12 && item.number !== element.number);
  const wrongNames = wrongFrom(nearby.map((item) => elementNames[item.number - 1]), name, `element-name:${element.number}`);
  const wrongSymbols = wrongFrom(nearby.map((item) => item.symbol), element.symbol, `element-symbol:${element.number}`);
  addQuestion({ category: "Bilim", prompt: `${name} elementinin kimyasal simgesi hangisidir?`, correct: element.symbol, wrong: wrongSymbols, explanation: `${name} elementinin simgesi ${element.symbol}'dir.`, factKey: `element-symbol:${element.number}` });
  addQuestion({ category: "Bilim", prompt: `${element.symbol} simgesi hangi elemente aittir?`, correct: name, wrong: wrongNames, explanation: `${element.symbol}, ${name} elementinin simgesidir.`, factKey: `element-symbol:${element.number}` });
  addQuestion({ category: "Bilim", prompt: `Periyodik tabloda ${name} hangi simgeyle gösterilir?`, correct: element.symbol, wrong: wrongSymbols, explanation: `${name} elementinin simgesi ${element.symbol}'dir.`, factKey: `element-symbol:${element.number}` });
  addQuestion({ category: "Bilim", prompt: `Kimyasal simgesi ${element.symbol} olan element hangisidir?`, correct: name, wrong: wrongNames, explanation: `${element.symbol}, ${name} elementinin simgesidir.`, factKey: `element-symbol:${element.number}` });
}

for (const item of manualQuestions) addQuestion(item);

fs.writeFileSync(path.join(root, "data/manual_questions.json"), `${JSON.stringify(manualQuestions, null, 2)}\n`);

const schemaPath = path.join(root, "app/schemas/com.bilgec.app.data.BilGecDatabase/1.json");
if (!fs.existsSync(schemaPath)) throw new Error("Önce ./gradlew compileDebugKotlin çalıştırılarak Room şeması üretilmeli.");
const schema = JSON.parse(fs.readFileSync(schemaPath, "utf8"));
const SQL = await initSqlJs({ locateFile: (file) => path.join(root, "node_modules/sql.js/dist", file) });
const database = new SQL.Database();
for (const entity of schema.database.entities) database.run(entity.createSql.replaceAll("${TABLE_NAME}", entity.tableName));
for (const entity of schema.database.entities) for (const index of entity.indices || []) database.run(index.createSql.replaceAll("${TABLE_NAME}", entity.tableName));
for (const query of schema.database.setupQueries || []) database.run(query);

database.run("BEGIN TRANSACTION");
const insert = database.prepare("INSERT INTO questions (id, category, prompt, option_0, option_1, option_2, option_3, correct_index, explanation, fact_key, image_asset, reviewed) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
for (const question of questions) insert.run([question.id, question.category, question.prompt, ...question.options, question.correctIndex, question.explanation, question.factKey, question.imageAsset, question.reviewed ? 1 : 0]);
insert.free();
database.run("COMMIT");

const assetDirectory = path.join(root, "app/src/main/assets/database");
fs.mkdirSync(assetDirectory, { recursive: true });
fs.writeFileSync(path.join(assetDirectory, "bilgec.db"), Buffer.from(database.export()));
database.close();

const flagDirectory = path.join(root, "app/src/main/assets/flags");
fs.mkdirSync(flagDirectory, { recursive: true });
for (const country of countries) {
  const code = country.cca2.toLowerCase();
  const output = path.join(flagDirectory, `${code}.png`);
  if (fs.existsSync(output)) continue;
  const response = await fetch(`https://raw.githubusercontent.com/lipis/flag-icons/main/flags/4x3/${code}.svg`);
  if (!response.ok) throw new Error(`${code} bayrağı indirilemedi: ${response.status}`);
  await sharp(Buffer.from(await response.arrayBuffer())).resize(320, 240, { fit: "contain" }).png().toFile(output);
}

console.log(`${questions.length} soru, ${countries.length} bayrak ve SQLite veritabanı üretildi.`);
