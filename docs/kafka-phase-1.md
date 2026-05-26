# Kafka Phase 1

## Bu Fazda Ne Yapmaya Calistik?
Bu fazin amaci Kafka'yi hemen projeye baglamak degildi. Once sunu netlestirmek istedik:

"Bu uygulamada hangi seyler event olmali?"

Kafka kullanacaksak once business olaylarini tanimlamamiz gerekir. Cunku Kafka kendi basina bir business mantigi degildir; sadece olaylari bir yerden baska bir yere tasiyan omurgadir. Ne tasiyacagini bilmeden Kafka baglamak, kablo cekip neye bagladigini bilmemek gibi olur.

Bu yuzden Faz 1'de sunu yaptik:
- Todo uygulamasinda hangi anlar "olay" sayilir, bunu belirledik
- Bu olaylari temsil edecek siniflari ekledik
- Olaylarin nasil ortak bir formatta tasinacagini belirledik
- Uygulamanin ileride Kafka'ya event gonderecegi noktayi hazirladik
- Ama henuz gercek Kafka'ya baglanmadik

Kisacasi:
- Faz 1 = "event dusuncesini projeye yerlestirme"
- Faz 1 degil = "Kafka broker'a veri gonderme"

## Faz 1'den Once Sistem Nasil Calisiyordu?
Bu projede todo islemleri zaten calisiyordu:
- kullanici todo olusturuyordu
- guncelliyordu
- siliyordu
- tamamliyordu

Bu islemlerin asıl mantigi `TodoServiceImpl` icinde yuruyordu.

Ayrica sistemde zaten bazi yan etkiler vardi:
- todo olusturulunca email actor calisabiliyordu
- todo tamamlaninca email gidebiliyordu
- due date scheduler actor ile hatirlatma yapilabiliyordu

Yani sistem zaten "bir islem oldu, sonra bir sey daha tetiklendi" mantigina yabanci degildi. Bu bizim icin iyi bir baslangicti.

Ama eksik olan sey sunuydu:
- "todo olusturuldu" gibi bir olayi resmi bir domain event olarak tanimlamiyorduk
- bu olayi standart bir veri yapisina koymuyorduk
- uygulamanin "burada event disari cikar" diye bir siniri yoktu

## Teoride Neyi Cozuyoruz?
Kafka'ya geciste en onemli zihniyet degisimi sudur:

Eskiden:
- kullanici islem yapar
- uygulama dogrudan sonucu uretir
- yan etkiler uygulamanin icinde daginik sekilde olur

Event-driven dusunce ile:
- kullanici islem yapar
- uygulama business sonucu uretir
- uygulama "bu olay oldu" diye bir event uretir
- diger sistemler bu event'i kullanir

Burada "event" su demektir:
- sistemde gercekten onemli bir sey oldu
- bu olayi baskalari bilmek isteyebilir
- bu olayin bir anlami vardir

Bizim projede bunun ilk ornekleri:
- `TodoCreated`
- `TodoUpdated`
- `TodoDeleted`
- `TodoCompleted`

## Bu Projede Tam Olarak Ne Yaptik?

### 1. Domain eventleri tanimladik
Yeni Kafka event tipleri ekledik:
- `TodoCreated`
- `TodoUpdated`
- `TodoDeleted`
- `TodoCompleted`

Bunlar su soruya cevap veriyor:
"Bu uygulamada hangi business olaylarini disari duyurmak istiyoruz?"

Bu cok onemli cunku ileride Kafka consumer yazarken artik kafamizda degil, kodda tanimli eventler olacak.

### 2. Ortak event zarfi olusturduk
Tum eventler icin ortak bir envelope tanimladik:
- `eventId`
- `eventType`
- `eventVersion`
- `occurredAt`
- `tenantId`
- `userId`
- `entityType`
- `entityId`
- `correlationId`
- `payload`

Bunu sunun icin yaptik:
- yarin `TodoCreated` da gelse
- obur gun `TodoCompleted` da gelse
- hepsinin ortak bir ust yapisi olsun

Bu, ileride Kafka'ya basarken de, outbox'a yazarken de, consumer okurken de cok isimize yarayacak.

### 3. Event factory ekledik
Event'i service'in icinde elle elle kurmak yerine `TodoEventFactory` ekledik.

Neden?
Cunku service'in isi business akisi olmali. Event'in JSON benzeri yapisini, metadata'sini, versiyon bilgisini her seferinde service'in icinde kurarsak servisler karmasiklasir.

Yani:
- service = "hangi olay oldu?"
- factory = "bu olayi standart event nesnesine cevir"

Bu ayirim ileride cok isimize yarayacak.

### 4. Publisher sinirini hazirladik
`TodoEventPublisher` diye bir arayuz ekledik.

Bu arayuzun anlami su:
"Buradan sonra event uygulama disina cikabilir."

Ama Faz 1'de gercek Kafka'ya gitmek istemedigimiz icin `NoOpTodoEventPublisher` kullandik.

`NoOp` ne demek?
- event'i aliyor
- ama hicbir yere gondermiyor
- sadece akisi bozmadan donuyor

Bu cok faydali cunku:
- kodu simdiden event gonderecek sekilde duzenliyoruz
- ama Kafka olmadan da uygulama calisiyor

### 5. Kafka kodlarini ayri klasore tasidik
Kafka ile ilgili siniflari genel `services` altina yigmadik.
Onlari ayri bir module aldik:
- `app/kafka/events`
- `app/kafka/publisher`

Bu neden onemli?
Cunku proje buyudukce sunlar da gelecek:
- outbox
- producer
- worker
- consumer kontratlari
- serialization

Hepsinin tek bir `kafka` agaci altinda toplanmasi projeyi daha anlasilir yapar.

### 6. `TodoServiceImpl` icinde event noktalarini belirledik
Burada kritik karar su oldu:
- `createTodo` sonrasi `TodoCreated`
- `updateTodo` sonrasi `TodoUpdated`
- `deleteTodo` sonrasi `TodoDeleted`
- `toggleTodo` icinde `false -> true` ise `TodoCompleted`

Buradaki en onemli tasarim karari:
- `updateTodo` icinde ayrica `TodoCompleted` uretmiyoruz

Neden?
Cunku completion olayi tek ve net bir business akista temsil edilsin istedik. Yani "tamamlandi" demek, bu projede `toggleTodo` tarafinda anlam kazaniyor.

Bu tip kararlar ilerde consumer davranisini sade tutar.

## Faz 1'den Sonra Sistem Nasil Degisti?
Kullanici acisindan neredeyse hicbir sey degismedi.

Uygulama hala:
- todo olusturuyor
- guncelliyor
- siliyor
- tamamliyor
- email actor akisini calistiriyor

Ama mimari acisindan buyuk bir fark oldu:
- sistem artik event dusuncesini taniyor
- eventleri standart yapida uretebiliyor
- event cikis sinirini biliyor

Yani kullanici ayni uygulamayi goruyor, ama icerideki yapi Kafka'ya uygun hale gelmeye basliyor.

## Bu Fazda Neyi Bilerek Yapmadik?
Bu kisim da cok onemli. Faz raporlarini okurken sadece "ne yaptik" degil "neyi bilerek yapmadik" da bilinmeli.

Faz 1'de sunlari bilerek yapmadik:
- gercek Kafka producer baglamadik
- Kafka dependency eklemedik
- outbox yazmadik
- DB transaction tarafina girmedik
- retry, DLQ, worker eklemedik

Yani Faz 1'de hedef:
"Kafka kullanacak dili olusturmak"

Ama hedef degil:
"Kafka'ya veri gondermek"

## Testte Neyi Kanitladik?
Bu fazda iki sey kanitlanmaliydi:

### 1. Event factory gercekten dogru event uretmeli
Eklenen test:
- `TodoEventFactorySpec`

Bu test su sorulara cevap veriyor:
- event tipi dogru mu
- ortak kontrat alanlari doluyor mu
- payload icinde gerekli veri var mi
- `TodoCompleted` tamamlanmis state'i dogru tasiyor mu

Yani event yapisinin kagit ustunde degil, kodda gercekten dogru calistigini gosterdik.

### 2. Publisher siniri uygulamayi bozmamali
Eklenen test:
- `NoOpTodoEventPublisherSpec`

Bu test su soruya cevap veriyor:
- sistem event publish etmeye hazir hale gelirken, Kafka yokken uygulama bozuluyor mu?

Cevap:
- hayir, bozulmuyor

Bu da bize su guveni veriyor:
- Faz 1'de yaptigimiz refactor kullanici akislarini kirmadan calisiyor

### 3. Genel sistem de kirilmamali
Mevcut `HomeControllerSpec` de gecti.

Bu da bize sunu soyluyor:
- yeni Kafka iskeleti projeyi genel anlamda bozmadı

## Faz 1 Sonunda Calistirdigimiz Komut
Asagidaki komut calistirildi:

```powershell
sbt test
```

Test sonucu:
- toplam 6 test gecti
- hata yok

Bu, Faz 1'in "kod yazildi" degil, "kod calisiyor" seviyesinde dogrulandigi anlamina gelir.

## Faz 1 Bize Ne Kazandirdi?
Bu fazdan sonra proje:
- event ureten bir yapıya dogru adim atti
- Kafka'ya baglanmadan once hangi olaylari tasiyacagini netlestirdi
- uygulamanin icine Kafka sinirlarini dogru yerlestirmeye basladi

Kisacasi Faz 1 bize teknoloji degil, dil kazandirdi.
Yani sistem artik "event" diliyle dusunmeye basladi.

## Hala Ne Eksikti?
Faz 1 bitince hala su eksikler vardi:
- event kaybolmadan saklanmiyordu
- gercek Kafka'ya gitmiyordu
- retry yoktu
- persistence garantisi yoktu

Bu nedenle Faz 2 gerekliydi.

## Faz 2 Neden Gerekiyordu?
Faz 1'de event olusturabiliyorduk ama guvenli sekilde saklamiyorduk.

Bu da su riski dogurur:
- todo DB'ye yazilir
- event sonra gonderilemez
- business veri vardir ama event yoktur

Iste Faz 2'nin problemi buydu.
Faz 2'de event'i kaybetmemeyi ogrenecegiz.
